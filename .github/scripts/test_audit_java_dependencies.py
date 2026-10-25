import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('java_audit', Path(__file__).with_name('audit_java_dependencies.py'))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class JavaAuditTest(unittest.TestCase):
    def test_versioned_inventory_is_fully_scanned_and_advisories_fail(self):
        with tempfile.TemporaryDirectory() as temporary:
            inventory, report = Path(temporary) / 'inventory.json', Path(temporary) / 'report.json'
            coordinates = [{'package': {'ecosystem': 'Maven', 'name': f'example:lib{i}'}, 'version': '1.2.3'}
                           for i in range(101)]
            inventory.write_text(json.dumps(coordinates))
            with patch.object(audit, 'request_batch', side_effect=[([{}] * 100), [{'vulns': [{'id': 'GHSA-example'}]}]]) as query:
                with self.assertRaisesRegex(ValueError, 'Known vulnerabilities'):
                    audit.audit(inventory, report)
            self.assertEqual([len(call.args[0]) for call in query.call_args_list], [100, 1])
            findings = json.loads(report.read_text())['findings']
            self.assertEqual(findings[0]['package']['name'], 'example:lib100')
            self.assertEqual(findings[0]['version'], '1.2.3')

    def test_empty_graph_cannot_report_success(self):
        with tempfile.TemporaryDirectory() as temporary:
            inventory = Path(temporary) / 'inventory.json'
            inventory.write_text('[]')
            with self.assertRaisesRegex(ValueError, 'nonempty'):
                audit.audit(inventory, Path(temporary) / 'report.json')

    def test_service_failure_retries_then_fails(self):
        with patch.object(audit.urllib.request, 'urlopen', side_effect=OSError('offline')) as request, \
                patch.object(audit.time, 'sleep'):
            with self.assertRaises(OSError):
                audit.request_batch([{}])
        self.assertEqual(request.call_count, 3)

    def test_partial_or_malformed_service_results_fail(self):
        for body in [{}, {'results': []}, {'results': [None]}, {'results': [{'vulns': [None]}]},
                     {'results': [{'error': {'code': 500}}]},
                     {'results': [{'next_page_token': 'unread-advisories'}]}]:
            with self.subTest(body=body), patch.object(audit.urllib.request, 'urlopen') as request:
                request.return_value.__enter__.return_value = io.StringIO(json.dumps(body))
                with self.assertRaises(ValueError):
                    audit.request_batch([{}])
