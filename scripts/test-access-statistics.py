#!/usr/bin/env python3
"""Run the mapper's aggregate/pagination SQL on an isolated SQLite fixture.

No network or project database is used. CONCAT is registered with MySQL NULL
semantics. MyBatis XML binding/owner guards are also covered by the Java tests.
This checks query logic, not MySQL DDL/migration compatibility.
"""
import hashlib
import re
import sqlite3
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAPPER = ET.parse(ROOT / "src/main/resources/mapper/AccessEventMapper.xml").getroot()
FRAGMENTS = {node.attrib["id"]: node for node in MAPPER.findall("sql")}


def device_hash(app, device):
    return hashlib.sha256(f"cg:login-device:v1:{app}:{device.strip()}".encode()).hexdigest()


def bind_query(method, values):
    """Expand only the fixed tags used by this mapper; bind all values as params."""
    parameters = dict(values)

    def render(node):
        tag = node.tag
        if tag == "include":
            return render(FRAGMENTS[node.attrib["refid"]])
        if tag == "choose":
            return render(node.find("when") if parameters.get("appIds") else node.find("otherwise"))
        if tag == "foreach":
            ids = parameters[node.attrib["collection"]]
            placeholders = []
            for index, app_id in enumerate(ids):
                key = f"owner_app_{index}"
                parameters[key] = app_id
                placeholders.append("#{" + key + "}")
            return "(" + ",".join(placeholders) + ")"
        return (node.text or "") + "".join(render(child) + (child.tail or "") for child in node)

    node = MAPPER.find(f"select[@id='{method}']")
    sql = render(node)
    bindings = []

    def placeholder(match):
        bindings.append(parameters[match.group(1)])
        return "?"

    return re.sub(r"#\{(\w+)\}", placeholder, sql), bindings


class LoginAccessSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.row_factory = sqlite3.Row
        self.db.create_function("CONCAT", -1, lambda *args: None if None in args else "".join(map(str, args)))
        self.db.executescript("""
            CREATE TABLE access_event (id INTEGER PRIMARY KEY, event_type TEXT, app_id INTEGER,
                ref_id INTEGER, device_hash TEXT, client_ip TEXT, created_at TEXT);
            CREATE TABLE application (id INTEGER PRIMARY KEY, app_name TEXT, deleted INTEGER);
            INSERT INTO application VALUES (1, 'Owned A', 0), (2, 'Owned B', 0), (3, 'Other owner', 0);
        """)
        self.values = dict(appIds=[1, 2], start7d="2026-10-01 00:00:00", todayStart="2026-10-07 00:00:00",
                           recentCutoff="2026-10-07 11:55:00", start="2026-10-01 00:00:00",
                           end="2026-10-08 00:00:00", limit=10, offset=0)
        today = "2026-10-07 11:59:00"
        self.add("CARD_LOGIN", 1, 11, "A", today)
        self.add("CARD_LOGIN", 1, 11, "A", today)
        self.add("CARD_LOGIN", 1, 12, "B", today)
        self.add("CARD_LOGIN_FREE", 1, 0, "A", today)
        self.add("CARD_LOGIN_FREE", 1, 0, "A", today)
        self.add("CARD_LOGIN_FREE", 2, 0, "A", today)
        self.add("CARD_LOGIN_FREE", 1, 0, None, today)  # old free login: count, not a unique visitor
        self.add("CARD_LOGIN_FREE", 1, 0, "C", "2026-10-06 12:00:00")
        self.add("CARD_LOGIN", 1, 13, None, "2026-10-07 10:00:00")  # old paid login still has card ID
        self.add("CARD_LOGIN", 2, 11, "A", today)
        self.add("CARD_LOGIN", 3, 99, "Z", today)
        self.add("CARD_LOGIN_FREE", 3, 0, "Z", today)
        self.add("APP_USER_WS_LOGIN", 1, 7, "WS", today)
        self.add("CARD_LOGIN_FREE", 1, 0, "old", "2026-09-30 23:59:59")
        self.add("CARD_LOGIN_FREE", 1, 0, "next-day", "2026-10-08 00:00:00")

    def tearDown(self):
        self.db.close()

    def add(self, kind, app, ref, device, time):
        self.db.execute("INSERT INTO access_event (event_type,app_id,ref_id,device_hash,client_ip,created_at) "
                        "VALUES (?,?,?,?,?,?)", (kind, app, ref, device_hash(app, device) if device else None,
                                                 "192.0.2.1", time))

    def query(self, method):
        sql, params = bind_query(method, self.values)
        return self.db.execute(sql, params).fetchall()

    def test_repeated_logins_count_as_events_not_new_devices(self):
        row = self.query("selectLoginStats")[0]
        self.assertEqual(5, row["paid_card_login_today"])
        self.assertEqual(4, row["free_login_today"])
        self.assertEqual(4, row["active_card_today"])
        self.assertEqual(2, row["active_visitor_today"])
        self.assertEqual(3, row["active_device_today"])

    def test_history_and_seven_day_window(self):
        row = self.query("selectLoginStats")[0]
        self.assertEqual(4, row["active_card7d"])
        self.assertEqual(3, row["active_visitor7d"])
        self.assertEqual(4, row["active_device7d"])
        self.assertEqual(1, row["unidentified_free_login7d"])

    def test_recent_activity_does_not_guess_continuous_online_time(self):
        row = self.query("selectLoginStats")[0]
        self.assertEqual(3, row["recent_card_count"])
        self.assertEqual(2, row["recent_visitor_count"])
        self.values["recentCutoff"] = "2026-10-07 12:01:00"
        row = self.query("selectLoginStats")[0]
        self.assertEqual(0, row["recent_card_count"])
        self.assertEqual(0, row["recent_visitor_count"])
        self.assertEqual(4, row["active_card_today"])

    def test_empty_ownership_never_reads_other_users(self):
        self.values["appIds"] = []
        self.assertTrue(all(value == 0 for value in self.query("selectLoginStats")[0]))
        self.assertEqual(0, self.query("countRecentLogins")[0][0])
        self.assertEqual([], self.query("selectRecentLogins"))

    def test_recent_records_are_scoped_paginated_and_stably_ordered(self):
        self.assertEqual(10, self.query("countRecentLogins")[0][0])
        self.values["limit"] = 4
        first = self.query("selectRecentLogins")
        self.values["offset"] = 4
        second = self.query("selectRecentLogins")
        self.assertEqual(4, len(first))
        self.assertEqual(4, len(second))
        self.assertFalse(set(row["id"] for row in first) & set(row["id"] for row in second))
        self.assertEqual([10, 7, 6, 5], [row["id"] for row in first])
        self.assertTrue(all(row["app_id"] in (1, 2) for row in first + second))
        self.assertEqual("card_11", first[0]["identity_id"])
        self.assertIsNone(first[1]["identity_id"])
        self.assertEqual("visitor_" + device_hash(2, "A"), first[2]["identity_id"])

    def test_window_boundaries_are_start_inclusive_end_exclusive(self):
        self.add("CARD_LOGIN_FREE", 1, 0, "start", "2026-10-01 00:00:00")
        self.add("CARD_LOGIN_FREE", 1, 0, "today-start", "2026-10-07 00:00:00")
        self.assertEqual(12, self.query("countRecentLogins")[0][0])
        row = self.query("selectLoginStats")[0]
        self.assertEqual(5, row["active_visitor7d"])
        self.assertEqual(3, row["active_visitor_today"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
