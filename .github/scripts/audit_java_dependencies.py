#!/usr/bin/env python3
"""Fail closed on known advisories in the complete resolved Maven runtime graph.

OSV's version-aware query API is documented at https://google.github.io/osv.dev/api/.
Network errors and malformed/partial responses are errors, never clean scan results.
No vulnerability suppressions are maintained: fixes must pass the actual baseline.
"""
import argparse
import json
import re
from pathlib import Path
import time
import urllib.error
import urllib.request

ENDPOINT = 'https://api.osv.dev/v1/querybatch'


def request_batch(queries):
    request = urllib.request.Request(ENDPOINT, json.dumps({'queries': queries}).encode(),
                                     {'Content-Type': 'application/json', 'User-Agent': 'CipherGate-dependency-audit'})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                body = json.load(response)
            results = body.get('results') if isinstance(body, dict) else None
            if not isinstance(results, list) or len(results) != len(queries):
                raise ValueError('OSV returned an incomplete batch response')
            for result in results:
                if (not isinstance(result, dict) or 'error' in result or result.get('next_page_token')
                        or not isinstance(result.get('vulns', []), list)):
                    raise ValueError('OSV returned an invalid vulnerability result')
                for vulnerability in result.get('vulns', []):
                    if not isinstance(vulnerability, dict) or not isinstance(vulnerability.get('id'), str):
                        raise ValueError('OSV returned an invalid advisory identifier')
            return results
        except (OSError, urllib.error.URLError):
            if attempt == 2:
                raise
            time.sleep(2 ** attempt)


def audit(inventory, report):
    queries = json.loads(Path(inventory).read_text(encoding='utf-8'))
    if not isinstance(queries, list) or not queries:
        raise ValueError('Resolved runtime inventory must be nonempty')
    for query in queries:
        if (not isinstance(query, dict) or not isinstance(query.get('package'), dict)
                or query['package'].get('ecosystem') != 'Maven'
                or not isinstance(query['package'].get('name'), str)
                or not re.fullmatch(r'[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+', query['package']['name'])
                or not isinstance(query.get('version'), str) or not query['version'].strip()):
            raise ValueError('Invalid resolved Maven coordinate')
    findings = []
    for offset in range(0, len(queries), 100):
        batch = queries[offset:offset + 100]
        results = request_batch(batch)
        for query, result in zip(batch, results):
            ids = sorted({vulnerability['id'] for vulnerability in result.get('vulns', [])})
            if ids:
                findings.append({**query, 'advisories': ids})
    path = Path(report)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({'source': ENDPOINT, 'packageCount': len(queries), 'findings': findings},
                               indent=2) + '\n', encoding='utf-8')
    if findings:
        for finding in findings:
            print(f"{finding['package']['name']}:{finding['version']}: {', '.join(finding['advisories'])}")
        raise ValueError('Known vulnerabilities remain in the Java runtime baseline')
    print(f'OSV baseline audit passed: {len(queries)} resolved Maven packages')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('inventory', type=Path)
    parser.add_argument('--report', type=Path, default=Path('build/reports/security/java-audit.json'))
    args = parser.parse_args()
    try:
        audit(args.inventory, args.report)
    except (OSError, ValueError, urllib.error.URLError) as error:
        parser.exit(1, f'Java dependency audit failed: {error}\n')


if __name__ == '__main__':
    main()
