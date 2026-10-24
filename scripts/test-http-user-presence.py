#!/usr/bin/env python3
"""Exercise production HTTP-presence SQL on an isolated SQLite fixture.

Reads the fixed Java SQL predicate rather than maintaining a copied query.
Checks time-window, eligibility, ownership, dedup and read-only semantics.
This is not a MySQL EXPLAIN/performance or live-server integration test.
"""
import re
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / 'src/main/java/com/ayssu/ciphergate/service/AppUserPresenceService.java').read_text()
PREDICATE = re.search(r'HTTP_ACTIVE_SQL\s*=\s*"""(.*?)""";', SOURCE, re.S).group(1)
PREDICATE = PREDICATE.replace('{0}', ':cutoff').replace('{1}', ':now')


class HttpPresenceSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.db.executescript('''
            CREATE TABLE application (id INTEGER PRIMARY KEY, deleted INTEGER, status INTEGER, business_model INTEGER);
            CREATE TABLE app_user (id INTEGER PRIMARY KEY, app_id INTEGER, deleted INTEGER, member_expires_at TEXT);
            CREATE TABLE app_user_binding (id INTEGER PRIMARY KEY, app_id INTEGER, user_id INTEGER, deleted INTEGER, is_banned INTEGER);
            CREATE TABLE access_event (id INTEGER PRIMARY KEY, event_type TEXT, app_id INTEGER, ref_id INTEGER, created_at TEXT);
            CREATE INDEX idx_access_login_stats ON access_event(app_id,event_type,created_at,ref_id);
            INSERT INTO application VALUES (10,0,1,1),(20,0,1,2);
            INSERT INTO app_user VALUES (1,10,0,'2026-10-08 21:00:00'),(2,20,0,NULL);
        ''')
        self.bindings = {'cutoff': '2026-10-08 19:55:00', 'now': '2026-10-08 20:00:00'}

    def tearDown(self):
        self.db.close()

    def login(self, user=1, app=10, when='2026-10-08 19:59:59', event='APP_USER_HTTP_LOGIN'):
        self.db.execute('INSERT INTO access_event(event_type,app_id,ref_id,created_at) VALUES (?,?,?,?)',
                        (event, app, user, when))

    def active(self):
        return [r[0] for r in self.db.execute('SELECT id FROM app_user WHERE ' + PREDICATE + ' ORDER BY id', self.bindings)]

    def test_five_minute_boundary_is_exclusive_and_now_is_inclusive(self):
        self.login(when='2026-10-08 19:55:00')
        self.assertEqual([], self.active())
        self.login(when='2026-10-08 19:55:01')
        self.assertEqual([1], self.active())
        self.db.execute('DELETE FROM access_event')
        self.login(when='2026-10-08 20:00:00')
        self.assertEqual([1], self.active())
        self.db.execute('DELETE FROM access_event')
        self.login(when='2026-10-08 20:00:01')
        self.assertEqual([], self.active())

    def test_refresh_expires_then_successful_login_extends_window(self):
        self.login(when='2026-10-08 20:00:00')
        self.assertEqual([1], self.active())
        self.bindings.update(cutoff='2026-10-08 20:00:00', now='2026-10-08 20:05:00')
        self.assertEqual([], self.active())
        self.login(when='2026-10-08 20:04:59')
        self.assertEqual([1], self.active())

    def test_ws_card_and_failed_logins_do_not_establish_or_refresh_http(self):
        for event in ('APP_USER_WS_LOGIN', 'CARD_LOGIN', 'CARD_LOGIN_FREE', 'APP_USER_HTTP_LOGIN_FAILED'):
            self.login(event=event)
        self.assertEqual([], self.active())
        self.login(when='2026-10-08 19:54:59')
        self.assertEqual([], self.active())

    def test_free_user_needs_no_membership_but_paid_user_must_be_unexpired(self):
        self.login(); self.login(user=2, app=20)
        self.assertEqual([1, 2], self.active())
        for expiry in (None, '2026-10-08 20:00:00', '2026-10-08 19:59:59'):
            self.db.execute('UPDATE app_user SET member_expires_at=? WHERE id=1', (expiry,))
            self.assertEqual([2], self.active())

    def test_disabled_or_deleted_app_and_deleted_user_are_not_recently_online(self):
        self.login()
        self.db.execute('UPDATE application SET status=0 WHERE id=10')
        self.assertEqual([], self.active())
        self.db.execute('UPDATE application SET status=1,deleted=1 WHERE id=10')
        self.assertEqual([], self.active())
        self.db.execute('UPDATE application SET deleted=0 WHERE id=10')
        self.db.execute('UPDATE app_user SET deleted=1 WHERE id=1')
        self.assertEqual([], self.active())

    def test_banned_binding_is_excluded_and_deleted_binding_does_not_block(self):
        self.login()
        self.db.execute('INSERT INTO app_user_binding VALUES (1,10,1,0,1)')
        self.assertEqual([], self.active())
        self.db.execute('UPDATE app_user_binding SET deleted=1')
        self.assertEqual([1], self.active())
        self.db.execute('UPDATE app_user_binding SET deleted=0,is_banned=0')
        self.assertEqual([1], self.active())

    def test_other_app_binding_and_wrong_app_event_cannot_affect_presence(self):
        self.login(app=20)
        self.assertEqual([], self.active())
        self.login()
        self.db.execute('INSERT INTO app_user_binding VALUES (1,20,1,0,1)')
        self.assertEqual([1], self.active())

    def test_owned_app_scope_wraps_ws_or_http_and_counts_a_user_once(self):
        self.login(); self.login(); self.login(user=2, app=20)
        combined = '(app_user.id IN (1,2) OR ' + PREDICATE + ')'
        rows = self.db.execute('SELECT id FROM app_user WHERE app_id=10 AND ' + combined, self.bindings).fetchall()
        self.assertEqual([(1,)], rows)
        count = self.db.execute('SELECT COUNT(*) FROM app_user WHERE app_id=10 AND ' + combined, self.bindings).fetchone()[0]
        self.assertEqual(1, count)  # both WS and HTTP + repeated events, not counted twice

    def test_offline_filter_is_complement_and_no_ws_fallback_returns_http_users(self):
        self.login()
        self.assertEqual([1], self.active())
        sql = 'SELECT id FROM app_user WHERE id NOT IN (2) AND NOT ' + PREDICATE
        self.assertEqual([], self.db.execute(sql, self.bindings).fetchall())
        self.db.execute('DELETE FROM access_event')
        self.assertEqual([(1,)], self.db.execute(sql, self.bindings).fetchall())

    def test_repeated_reads_never_touch_login_events_or_extend_window(self):
        self.login()
        before = self.db.execute('SELECT * FROM access_event').fetchall()
        for _ in range(3): self.assertEqual([1], self.active())
        self.assertEqual(before, self.db.execute('SELECT * FROM access_event').fetchall())


if __name__ == '__main__':
    unittest.main(verbosity=2)
