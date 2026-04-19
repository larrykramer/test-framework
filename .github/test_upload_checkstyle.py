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

import atexit
import importlib.util
import os
import pathlib
import sys
import tempfile
import unittest
from contextlib import ExitStack
from unittest.mock import patch
from xml.etree import ElementTree

# Keep this temporary directory alive for the full test process so the imported
# module always sees a valid workspace path from the environment.
IMPORT_WORKSPACE = tempfile.TemporaryDirectory()
atexit.register(IMPORT_WORKSPACE.cleanup)

REPO = 'octocat/hello-world'

IMPORT_ENV = {
    'GITHUB_REPOSITORY': REPO,
    'GITHUB_WORKSPACE': IMPORT_WORKSPACE.name,
    'GITHUB_TOKEN': 'test-token',
    'CHECKS_SHA': 'deadbeaf',
    'GITHUB_SHA': 'deadbeaf',
}


def load_module_from_sibling(filename, module_name=None):
    test_dir = pathlib.Path(__file__).resolve().parent
    module_path = test_dir / filename

    name = module_name or module_path.stem

    spec = importlib.util.spec_from_file_location(name, module_path)
    module = importlib.util.module_from_spec(spec)

    sys.modules.pop(name, None)
    sys.modules[name] = module
    spec.loader.exec_module(module)

    return module


with patch.dict(os.environ, IMPORT_ENV, clear=False):
    target = load_module_from_sibling(
        'upload_checkstyle.py',
        'upload_checkstyle'
    )


class TestChunks(unittest.TestCase):
    def test_chunks_splits_list_with_remainder(self):
        items = [1, 2, 3, 4, 5]
        result = list(target.chunks(items, 2))
        self.assertEqual([[1, 2], [3, 4], [5]], result)

    def test_chunks_splits_exact_multiple(self):
        items = [1, 2, 3, 4, 5, 6]
        result = list(target.chunks(items, 3))
        self.assertEqual([[1, 2, 3], [4, 5, 6]], result)

    def test_chunks_returns_single_chunk_when_size_exceeds_length(self):
        items = [1, 2, 3]
        result = list(target.chunks(items, 10))
        self.assertEqual([[1, 2, 3]], result)

    def test_chunks_returns_empty_for_empty_input(self):
        result = list(target.chunks([], 4))
        self.assertEqual([], result)

    def test_chunks_with_size_one_returns_single_item_chunks(self):
        items = ['a', 'b', 'c']
        result = list(target.chunks(items, 1))
        self.assertEqual([['a'], ['b'], ['c']], result)

    def test_chunks_preserves_order(self):
        items = ['first', 'second', 'third', 'fourth']
        result = list(target.chunks(items, 2))
        self.assertEqual([['first', 'second'], ['third', 'fourth']], result)

    def test_chunks_does_not_modify_input(self):
        items = [1, 2, 3, 4]
        original = items.copy()

        list(target.chunks(items, 2))

        self.assertEqual(original, items)

    def test_chunks_zero_size_raises_value_error(self):
        with self.assertRaises(ValueError):
            list(target.chunks([1, 2, 3], 0))


class WorkspaceTestCase(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)

        self.workspace = pathlib.Path(self.temp_dir.name).resolve()

        workspace_patcher = patch.object(target, "WORKSPACE", self.workspace)
        workspace_patcher.start()
        self.addCleanup(workspace_patcher.stop)

    def make_workspace_file(self, relative_path):
        return self.make_file(self.workspace, relative_path)

    def make_reports_file(self, root = ''):
        base = pathlib.Path(root) if root else self.workspace
        if not base.is_absolute():
            base = self.workspace / base
        return str(self.make_file(base, 'reports/checkstyle-result.xml'))

    @staticmethod
    def make_file(root, relative_path, contents = 'test\n'):
        path = pathlib.Path(root) / pathlib.Path(relative_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents, encoding='utf-8')
        return path


class TestNormalizePath(WorkspaceTestCase):
    def test_abs_in_workspace_returns_relative(self):
        source_file = self.make_workspace_file('src/pkg/file.py')
        report_file = self.make_reports_file()

        result = target.normalize_path(str(source_file), report_file)

        self.assertEqual('src/pkg/file.py', result)

    def test_abs_outside_workspace_returns_basename(self):
        report_file = self.make_reports_file()

        with tempfile.TemporaryDirectory() as other_dir:
            outside_file = pathlib.Path(other_dir) / 'outside.py'
            outside_file.write_text('x\n', encoding='utf-8')

            result = target.normalize_path(str(outside_file), report_file)

        self.assertEqual('outside.py', result)

    def test_windows_abs_path_returns_basename(self):
        report_file = self.make_reports_file()
        result = target.normalize_path(
            r'C:\temp\Example.java',
            str(report_file),
        )
        self.assertEqual('Example.java', result)

    def test_repo_relative_found_in_workspace(self):
        self.make_workspace_file('app/main.py')
        report_file = self.make_reports_file('module')

        result = target.normalize_path('app/main.py', report_file)

        self.assertEqual('app/main.py', result)

    def test_backslashes_normalized_for_repo_match(self):
        self.make_workspace_file('app/main.py')
        report_file = self.make_reports_file()

        result = target.normalize_path(r'app\main.py', report_file)

        self.assertEqual('app/main.py', result)

    def test_workspace_match_precedes_report_search(self):
        self.make_workspace_file('app/main.py')
        self.make_workspace_file('module/app/main.py')
        report_file = self.make_reports_file('module')

        result = target.normalize_path('app/main.py', report_file)

        self.assertEqual('app/main.py', result)

    def test_walk_up_from_report_dir_finds_file(self):
        self.make_workspace_file('module/src/Main.java')
        report_file = self.make_reports_file('module/build')

        result = target.normalize_path('src/Main.java', report_file)

        self.assertEqual('module/src/Main.java', result)

    def test_walk_uses_nearest_ancestor_match(self):
        self.make_workspace_file('a/local/file.py')
        self.make_workspace_file('a/b/local/file.py')
        report_file = self.make_reports_file('a/b/c')

        result = target.normalize_path('local/file.py', report_file)

        self.assertEqual('a/b/local/file.py', result)

    def test_walk_can_find_file_at_workspace_root(self):
        self.make_workspace_file('shared/util.py')
        report_file = self.make_reports_file('deep/nested')

        result = target.normalize_path('shared/util.py', report_file)

        self.assertEqual('shared/util.py', result)

    def test_no_match_returns_normalized_original(self):
        report_file = self.make_reports_file()
        result = target.normalize_path(r'foo\bar//../baz.py', report_file)
        self.assertEqual("foo/baz.py", result)

    def test_dot_returned_when_current_dir_not_found(self):
        report_file = self.make_reports_file()
        result = target.normalize_path('.', report_file)
        self.assertEqual('.', result)

    def test_report_outside_workspace_not_searched(self):
        with tempfile.TemporaryDirectory() as other_dir:
            external_report_file = self.make_reports_file(other_dir)
            self.make_file(other_dir, 'src/outside.py')

            result = target.normalize_path(
                'src/outside.py',
                external_report_file,
            )

        self.assertEqual('src/outside.py', result)


class TestMain(WorkspaceTestCase):
    # Test Constants
    _FILE_PATH = 'src/main/java/com/example/Main.java'

    _REPORT_GLOB = '**/checkstyle-report.xml'

    _CHECK_NAME = 'Checkstyle Smoke'
    _SHA = 'deadbeef'

    _API = f'https://api.github.com/repos/{REPO}'

    def make_checkstyle_report(self, issues):
        report = ElementTree.Element('checkstyle', version='10.0')
        file_node = ElementTree.SubElement(report, 'file', name=self._FILE_PATH)

        annotations = []

        for issue in issues:
            line = int(issue['line'])
            severity = issue['severity']
            message = issue['message']

            error_attrs = {
                'line': str(line),
                'severity': severity,
                'message': message,
            }
            annotation = {
                'path': self._FILE_PATH,
                'start_line': line,
                'end_line': line,
                'message': message
            }

            if severity.lower() == 'error':
                annotation['annotation_level'] = 'failure'
            elif severity.lower() == 'warning':
                annotation['annotation_level'] = 'warning'
            elif severity != 'warning':
                annotation['annotation_level'] = 'notice'

            column = issue.get('column')
            if column is not None:
                error_attrs['column'] = str(column)
                if str(column).isdigit():
                    annotation['start_column'] = int(column)
                    annotation['end_column'] = int(column)

            source = issue.get('source', '')
            if source:
                error_attrs['source'] = source
                annotation['title'] = source.rsplit('.', 1)[-1]
                annotation['message'] += f'\n{source}'
            else:
                annotation['title'] = 'Checkstyle'

            ElementTree.SubElement(file_node, 'error', error_attrs)
            annotations.append(annotation)

        report_xml = '<?xml version="1.0" encoding="UTF-8"?>'
        report_xml += ElementTree.tostring(report, encoding='unicode')

        report_file = self.make_file(
            self.workspace,
            'reports/checkstyle-report.xml',
            report_xml
        )

        return report_file, annotations

    def run_main_with_mocks(self, report_files, post_id):
        api_calls = []

        def fake_gh(method, url, payload):
            api_calls.append((method, url, payload))
            if method == 'POST':
                return {'id': post_id}
            return {}

        with ExitStack() as stack:
            stack.enter_context(
                patch.object(target.glob, 'glob', return_value=report_files)
            )
            stack.enter_context(
                patch.object(target, 'REPORT_GLOB', self._REPORT_GLOB)
            )
            stack.enter_context(
                patch.object(target, 'CHECK_NAME', self._CHECK_NAME)
            )
            stack.enter_context(patch.object(target, 'SHA', self._SHA))
            stack.enter_context(patch.object(target, 'API', self._API))

            stack.enter_context(
                patch.object(target, 'gh', side_effect=fake_gh)
            )

            target.main()

        return api_calls

    def test_main_posts_and_patches_expected_api_payloads(self):
        # Arrange
        self.make_workspace_file(self._FILE_PATH)

        report_file, expected_annotations = self.make_checkstyle_report([
            *[
                {
                    'line': i,
                    'column': i,
                    'severity': 'error',
                    'message': f'Error {i}',
                    'source': 'com.example.ErrorRule',
                }
                for i in range(1, 51)
            ],
            {
                'line': 51,
                'column': 7,
                'severity': 'warning',
                'message': 'Warn 51',
                'source': 'com.example.WarningRule',
            },
            {
                'line': 52,
                'severity': 'info',
                'message': 'Notice 52',
                'source': 'com.example.NoticeRule',
            },
        ])

        expected_summary = (
            "Scanned 1 report(s) matching '**/checkstyle-report.xml'. "
            "Found 52 issue(s): 50 error(s), 1 warning(s), 1 notice(s)."
        )

        # Act
        api_calls = self.run_main_with_mocks(
            report_files=[str(report_file)],
            post_id=321
        )

        # Assert
        self.assertEqual(2, len(api_calls))

        post_method, post_url, post_payload = api_calls[0]
        self.assertEqual('POST', post_method)
        self.assertEqual(self._API + '/check-runs', post_url)
        self.assertEqual(
            {
                'name': self._CHECK_NAME,
                'head_sha': self._SHA,
                'status': 'completed',
                'conclusion': 'failure',
                'output': {
                    'title': self._CHECK_NAME,
                    'summary': expected_summary,
                    'text': '\n'.join([
                        f"Matched pattern: '{self._REPORT_GLOB}'",
                        '',
                        'Reports:',
                        f"- '{report_file}'",
                    ]),
                    'annotations': expected_annotations[:50],
                },
            },
            post_payload
        )

        patch_method, patch_url, patch_payload = api_calls[1]
        self.assertEqual('PATCH', patch_method)
        self.assertEqual(self._API + '/check-runs/321', patch_url)
        self.assertEqual(
            {
                'output': {
                    'title': self._CHECK_NAME,
                    'summary': expected_summary,
                    'annotations': expected_annotations[50:],
                },
            },
            patch_payload
        )


if __name__ == '__main__':
    unittest.main()
