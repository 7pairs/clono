# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
"""Connect retained-link checks to all-original-context checks without editing the stopped probe."""
import copy
import time
from pypdf import PdfReader
from body_context import read_pages, locate_contexts, check_pages, check_named_starts
from two_pass import inspect_pdf, BASELINE


def inspect_rerun_pdf(path, profile, occurrences, stage):
    started = time.perf_counter()
    assert stage in ['first', 'final']
    fixed = {(r[1], r[2]): r[3] for r in profile['firstReferences']}
    rows = [(o['source'], o['id']) for o in occurrences]
    assert rows == [tuple(o[:2]) for o in BASELINE['occurrences']], 'Unexpected original identity/order'
    fixture_rows = BASELINE['occurrences']
    pages = read_pages(path)
    assert len(pages) == profile['pageCount']
    body = locate_contexts(pages, fixture_rows, BASELINE['destinationContexts'], profile['indexPages'])
    check_pages(body, fixed)
    wanted = {(r[1], r[2]) for r in profile['firstReferences' if stage == 'first' else 'finalReferences']}
    required = [o for o in occurrences if (o['source'].removesuffix('.md') + '.html', o['id']) in wanted]
    link_profile = copy.deepcopy(profile)
    # The old checker can inspect a selected set of required named targets; its link expectations stay fixed.
    link_profile['firstReferences'] = [r for r in profile['firstReferences'] if (r[1], r[2]) in wanted]
    result = inspect_pdf(path, link_profile, required, stage)
    reader = PdfReader(path)
    required_rows = [r for r in fixture_rows if (r[0].removesuffix('.md') + '.html', r[1]) in wanted]
    starts = check_named_starts({key: value for key, value in body.items() if key in wanted}, reader, required_rows, pages)
    result['namedContexts'] = result.pop('contexts')
    result['checks'].pop('allSevenContexts')  # The old name described its then-fixed input size.
    result['bodyContexts'] = list(body.values())
    result['namedStartChecks'] = starts
    names = reader.named_destinations
    result['omittedNamedTargets'] = [{'source': key[0], 'id': key[1],
                                     'names': [name for name in names if name.startswith('/viv-id-') and name.endswith(key[1])]}
                                    for key in fixed if key not in wanted]
    result['checks'].update({'allSevenBodyContexts': True, 'independentBodyPages': True, 'requiredNamedStarts': True})
    result['elapsedMs'] = round((time.perf_counter() - started) * 1000, 3)
    return result
