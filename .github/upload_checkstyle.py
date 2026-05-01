#!/usr/bin/env python3

#  Copyright (c) 2026 Larry Kramer
#
#  Permission is hereby granted, free of charge, to any person obtaining a copy
#  of this software and associated documentation files (the "Software"), to deal
#  in the Software without restriction, including without limitation the rights
#  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
#  copies of the Software, and to permit persons to whom the Software is
#  furnished to do so, subject to the following conditions:
#
#  The above copyright notice and this permission notice shall be included in
#  all copies or substantial portions of the Software.
#
#  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
#  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
#  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
#  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
#  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
#  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
#  SOFTWARE.

import glob
import json
import os
import re
import urllib.error
import urllib.request
from xml.etree import ElementTree

REPO = os.environ['GITHUB_REPOSITORY']
WORKSPACE = os.path.realpath(os.environ['GITHUB_WORKSPACE'])
TOKEN = os.environ['GITHUB_TOKEN']
SHA = os.environ.get('CHECKS_SHA') or os.environ['GITHUB_SHA']
REPORT_GLOB = os.environ.get('REPORT_GLOB', '**/checkstyle-result.xml')
CHECK_NAME = os.environ.get('CHECK_NAME', 'Checkstyle')

CHECKSTYLE_OUTCOME = os.environ.get('CHECKSTYLE_OUTCOME', 'success')

API = f'https://api.github.com/repos/{REPO}'

# GitHub Checks allows at most 1000 annotations per check run in total, even
# when they are uploaded across multiple PATCH calls.
MAX_ANNOTATIONS = 1000


def gh(method, url, payload):
    """
    Send an authenticated JSON request to the GitHub REST API.

    This helper builds a request using the workflow's GitHub token, sends the
    provided JSON payload, and returns the decoded JSON response body. If the
    API responds with an HTTP error, the error response body is printed before
    the exception is re-raised.

    Args:
        method: The HTTP method to use, such as 'POST' or 'PATCH'.
        url: The fully qualified GitHub API endpoint URL.
        payload: A JSON-serializable object to send as the request body.

    Returns:
        A Python object created by decoding the JSON response body.

    Raises:
        urllib.error.HTTPError: If GitHub returns a non-success HTTP status.
        urllib.error.URLError: If the request cannot be completed.
        json.JSONDecodeError: If the response body is not valid JSON.
    """
    headers = {
        'Authorization': f'Bearer {TOKEN}',
        'Accept': 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
        'Content-Type': 'application/json',
    }
    req = urllib.request.Request(url, data=json.dumps(payload).encode('utf-8'),
                                 method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        print(e.read().decode('utf-8'))
        raise


def chunks(items, size):
    """
    Yield successive slices of `items`, each containing up to `size` elements.

    Chunks are produced in the original order of the input list. All yielded
    chunks have exactly `size` elements except the last chunk, which may be
    shorter if the list length is not evenly divisible by `size`. If `items`
    is empty, nothing is yielded.

    Args:
        items: A sequence supporting `len()` and slicing.
        size: The maximum number of items per yielded chunk.

    Yields:
        Slices of `items`, each containing up to `size` elements.

    Raises:
        ValueError: If `size` is less than or equal to 0.
    """
    if size <= 0:
        raise ValueError(f"size must be positive, got {size}")
    for i in range(0, len(items), size):
        yield items[i:i + size]


def slashify(path):
    """Return `path` as a string with forward slashes."""
    return str(path).replace('\\', '/')


def is_relative_to(path, base):
    try:
        path = os.path.normcase(os.path.realpath(path))
        normalized_base = os.path.normcase(os.path.realpath(base))
        return os.path.commonpath([path, normalized_base]) == normalized_base
    except ValueError:
        return False


def to_workspace_path(path):
    try:
        path = str(os.path.realpath(path))
        if not is_relative_to(path, WORKSPACE):
            return None
        return slashify(os.path.relpath(path, WORKSPACE))
    except (ValueError, OSError):
        return None


def normalize_path(file_name, report_path):
    """
    Resolve a Checkstyle file path to a GitHub-annotation-friendly repository
    path.

    Checkstyle reports may contain absolute paths, repository-relative paths, or
    paths relative to the report's location. This function attempts to map the
    reported file name to a path relative to the GitHub workspace so it can be
    used in check run annotations.

    Resolution order:
        1. If `file_name` is absolute (including Windows drive-letter paths),
           resolve it and make it relative to `WORKSPACE`.
        2. If `file_name` exists directly under `WORKSPACE`, use that.
        3. Starting from the directory containing `report_path`, walk upward
           toward `WORKSPACE` and look for `file_name` relative to each
           directory.
        4. If no matching file is found, return a normalized string version of
           the original path.

    If an absolute path cannot be made relative to `WORKSPACE`, the file's
    basename is returned as a fallback.

    Args:
        file_name: The file path reported by Checkstyle.
        report_path: The path to the XML report currently being processed.

    Returns:
        A normalized path string suitable for GitHub annotations, preferably
        relative to the repository workspace.
    """
    file_name = str(slashify(file_name))
    if os.path.isabs(file_name) or re.match(r'^[A-Za-z]:[\\/]', file_name):
        path = to_workspace_path(os.path.realpath(file_name))
        if path is None:
            return os.path.basename(file_name)
        return path

    candidate = os.path.realpath(os.path.join(WORKSPACE, file_name))
    if os.path.exists(candidate):
        candidate = to_workspace_path(candidate)
        if candidate is not None:
            return candidate

    current = str(os.path.dirname(os.path.realpath(report_path)))
    while is_relative_to(current, WORKSPACE):
        candidate = os.path.realpath(os.path.join(current, file_name))
        if os.path.exists(candidate):
            candidate = to_workspace_path(candidate)
            if candidate is not None:
                return candidate

        if os.path.normcase(current) == os.path.normcase(WORKSPACE):
            break

        parent = os.path.dirname(current)
        if os.path.normcase(parent) == os.path.normcase(current):
            break

        current = parent

    return slashify(os.path.normpath(file_name))


def main():
    report_files = sorted(glob.glob(REPORT_GLOB, recursive=True))

    parse_errors = []

    annotations = []
    failure_count = 0
    warning_count = 0
    notice_count = 0

    for report in report_files:
        try:
            root = ElementTree.parse(report).getroot()
        except ElementTree.ParseError as e:
            parse_errors.append(f'{report}: {e}')
            continue

        for file_node in root.findall('file'):
            raw_path = file_node.attrib.get('name') or report
            path = normalize_path(raw_path, report)

            for error in file_node.findall('error'):
                line = int(error.attrib.get('line', '1') or '1')
                column = error.attrib.get('column')

                severity = (error.attrib.get('severity') or '').lower()
                if severity == 'error':
                    failure_count += 1
                    severity = 'failure'
                elif severity == 'warning':
                    warning_count += 1
                else:
                    notice_count += 1
                    severity = 'notice'

                message = error.attrib.get('message') or 'Checkstyle error'
                message = message.strip()

                source = (error.attrib.get('source') or '').strip()

                title = source.rsplit('.', 1)[-1] if source else 'Checkstyle'

                annotation = {
                    'path': str(path),
                    'start_line': line,
                    'end_line': line,
                    'annotation_level': severity,
                    'title': title,
                    'message': f'{message}\n{source}' if source else message
                }

                if column and column.isdigit():
                    annotation['start_column'] = int(column)
                    annotation['end_column'] = int(column)

                annotations.append(annotation)

    text_lines = [
        f'Matched pattern: \'{REPORT_GLOB}\'',
        '',
        'Reports:'
    ]
    if report_files:
        text_lines.extend(f'- \'{p}\'' for p in report_files[:100])
        if len(report_files) > 100:
            text_lines.append(f'- ...and {len(report_files) - 100} more')
    else:
        text_lines.append('- No reports found')

    if parse_errors:
        text_lines.extend(['', 'Parse errors:'])
        text_lines.extend(f'- {e}' for e in parse_errors[:20])

    text = '\n'.join(text_lines)
    if len(text) > 65535:
        text = text[:65530] + '\n...'

    # Intentionally treat any warning- or notice-level annotation as "neutral"
    # rather than "success". This makes the check report "not fully clean" even
    # when there are no failures, instead of showing a green checkmark.
    if CHECKSTYLE_OUTCOME == 'failure' and not report_files:
        conclusion = 'failure'
        summary = ('Maven build failed before Checkstyle reports could be '
                   'generated. See CI logs for details.')
    else:
        summary = (f'Scanned {len(report_files)} report(s) matching '
                   f'\'{REPORT_GLOB}\'. '
                   f'Found {len(annotations)} issue(s): '
                   f'{failure_count} error(s), {warning_count} warning(s), '
                   f'{notice_count} notice(s).')
        if len(annotations) > MAX_ANNOTATIONS:
            # Truncate only what we upload to satisfy the GitHub API limit. The
            # counts above, and the conclusion computed earlier, intentionally
            # reflect the full set of violations, including those we cannot
            # annotate individually.
            annotations = annotations[:MAX_ANNOTATIONS]
            summary += ' '
            summary += f'Uploaded only the first {MAX_ANNOTATIONS} annotations.'

        if CHECKSTYLE_OUTCOME == 'failure' or parse_errors or failure_count:
            conclusion = 'failure'
            if parse_errors:
                summary += ' '
                summary += f'{len(parse_errors)} report(s) could not be parsed.'
        elif warning_count or notice_count:
            conclusion = 'neutral'
        else:
            conclusion = 'success'

    response = gh('POST', f'{API}/check-runs', payload={
        'name': CHECK_NAME,
        'head_sha': SHA,
        'status': 'completed',
        'conclusion': conclusion,
        'output': {
            'title': CHECK_NAME,
            'summary': summary,
            'text': text,
            'annotations': annotations[:50],
        }
    })
    for chunk in chunks(annotations[50:], 50):
        gh('PATCH', f'{API}/check-runs/{response["id"]}', payload={
            'output': {
                'title': CHECK_NAME,
                'summary': summary,
                'annotations': chunk
            }
        })

    print(summary)


if __name__ == '__main__':
    main()
