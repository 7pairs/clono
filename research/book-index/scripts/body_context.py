# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
"""Fixed-fixture PDF context locator; not a general PDF ID recovery mechanism."""
import unicodedata
import pdfplumber
from pdfplumber.utils.text import chars_to_textmap
from page_aggregation import extract_page_map


class ContextError(ValueError):
    pass


def read_pages(path):
    with pdfplumber.open(path) as pdf:
        return [{'page': n, 'height': p.height, 'chars': [dict(c) for c in p.chars]}
                for n, p in enumerate(pdf.pages, 1)]


def locate_contexts(pages, occurrences, contexts, index_pages):
    lines = []
    for page in pages:
        if page['page'] in index_pages or not page['chars']:
            continue
        for line in chars_to_textmap(page['chars']).extract_text_lines():
            chars = line['chars']
            text, owners = '', []
            for char in chars:
                normalized = unicodedata.normalize('NFKC', char['text'])
                text += normalized
                owners.extend([char] * len(normalized))
            lines.append((page['page'], text, owners))
    found = {}
    for occurrence in occurrences:
        source, ident = occurrence[:2]
        key = (source.removesuffix('.md') + '.html', ident)
        context = unicodedata.normalize('NFKC', contexts[ident])
        if not context:
            raise ContextError('Empty context: ' + '#'.join(key))
        matches = []
        for page, text, owners in lines:
            offset = text.find(context)
            while offset >= 0:
                start, end = owners[offset], owners[offset + len(context) - 1]
                matches.append({'source': key[0], 'id': ident, 'physicalPage': page, 'context': context,
                                'x0': start['x0'], 'top': start['top'], 'x1': end['x1'], 'bottom': start['bottom']})
                offset = text.find(context, offset + 1)
        if len(matches) != 1:
            kind = 'Missing' if not matches else 'Ambiguous'
            raise ContextError(f"{kind} body context: {'#'.join(key)} ({len(matches)} starts)")
        found[key] = matches[0]
    if len(found) != len(occurrences):
        raise ContextError('Duplicate source/ID in fixture')
    return found


def check_pages(found, expected):
    if found.keys() != expected.keys():
        raise ContextError('Source/ID set mismatch')
    for key, page in expected.items():
        actual = found[key]['physicalPage']
        if actual != page:
            raise ContextError(f"Page mismatch: {'#'.join(key)} expected {page}, found {actual}")


def check_named_starts(found, reader, occurrences, pages, named=None):
    names = reader.named_destinations if named is None else named
    original = [{'source': o[0], 'id': o[1]} for o in occurrences]
    mapped = extract_page_map(reader, original, named=names)
    check_pages(found, {key: value['physicalPage'] for key, value in mapped.items()})
    heights = {page['page']: page['height'] for page in pages}
    comparisons = []
    for key, target in mapped.items():
        destination = names[target['destination']]
        left = float(destination['/Left'])
        top = heights[target['physicalPage']] - float(destination['/Top'])
        row = found[key]
        # Anchor top precedes the glyph box slightly in this fixed fixture.
        if abs(row['x0'] - left) > 1 or not -1 <= row['top'] - top <= 5:
            raise ContextError('Start mismatch: ' + '#'.join(key))
        comparisons.append({'source': key[0], 'id': key[1], 'anchorX': left, 'anchorTop': top,
                            'glyphX': row['x0'], 'glyphTop': row['top'], 'physicalPage': row['physicalPage']})
    return comparisons
