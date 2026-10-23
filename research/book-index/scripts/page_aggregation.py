# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
"""Research only: flat ASCII manuscript names and observed viv-id PDF names."""
import copy
import re
from urllib.parse import urlsplit


class PageMappingError(ValueError):
    pass


def reference_key(occurrence):
    return (occurrence['source'].removesuffix('.md') + '.html', occurrence['id'])


def extract_page_map(reader, occurrences, named=None):
    wanted = {reference_key(o) for o in occurrences}
    if len(wanted) != len(occurrences):
        raise PageMappingError('Duplicate manuscript/ID in collected occurrences')
    result = {}
    for name, destination in (reader.named_destinations if named is None else named).items():
        if not name.startswith('/viv-id-'):
            continue
        decoded = re.sub(r':([0-9a-fA-F]{4})', lambda match: chr(int(match[1], 16)), name[len('/viv-id-'):])
        uri = urlsplit(decoded)
        key = (uri.path.rsplit('/', 1)[-1], uri.fragment)
        if key not in wanted:
            continue
        label = '#'.join(key)
        if key in result:
            raise PageMappingError(f'Ambiguous destination for {label}')
        page = reader.get_destination_page_number(destination)
        if type(page) is not int or not 0 <= page < len(reader.pages):
            raise PageMappingError(f'Invalid physical page for {label}: {page}')
        result[key] = {'source': key[0], 'id': key[1], 'physicalPage': page + 1, 'destination': str(name)}
    missing = sorted(wanted - result.keys())
    if missing:
        raise PageMappingError('Missing PDF destinations: ' + ', '.join('#'.join(key) for key in missing))
    return result


def aggregate_pages(items, page_map):
    missing = [reference_key(o) for item in items for o in item['occurrences'] if reference_key(o) not in page_map]
    if missing:
        raise PageMappingError('Missing page mappings: ' + ', '.join('#'.join(key) for key in missing))
    grouped = []
    for item in items:
        before, kept, removed, seen = [], [], [], {}
        for occurrence in item['occurrences']:
            key = reference_key(occurrence)
            mapped = page_map[key]
            if (mapped['source'], mapped['id']) != key or type(mapped['physicalPage']) is not int or mapped['physicalPage'] < 1:
                raise PageMappingError('Invalid page mapping: ' + '#'.join(key))
            ref = {**copy.deepcopy(mapped), 'href': '#'.join(key), 'originalSource': occurrence['source'],
                   'position': copy.deepcopy(occurrence['position'])}
            before.append(ref)
            if ref['physicalPage'] in seen:
                first = seen[ref['physicalPage']]
                removed.append({**ref, 'keptSource': first['source'], 'keptId': first['id']})
            else:
                seen[ref['physicalPage']] = ref
                kept.append(copy.deepcopy(ref))
        # Each collected item has an exact (term, reading) identity; pages are grouped only inside it.
        grouped.append({'term': item['term'], 'reading': item['reading'], 'before': before, 'kept': kept, 'removed': removed})
    return {'items': grouped, 'beforeCount': sum(len(i['before']) for i in grouped),
            'afterCount': sum(len(i['kept']) for i in grouped)}
