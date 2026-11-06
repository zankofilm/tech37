#!/usr/bin/env python3
"""Static smoke audit. Does not replace tapping the Android UI or testing the teacher API."""
from pathlib import Path
import re, sys
root = Path(__file__).resolve().parents[1]
main = (root/'app/src/main/java/ir/madreseyar/teacher/MainActivity.kt').read_text()
sources = list((root/'app/src/main/java/ir/madreseyar/teacher').glob('*.kt'))
enum = re.search(r'private enum class Screen\s*\{([^}]*)}', main)
if not enum: sys.exit('FAIL no navigation enum')
valid = {name.strip() for name in enum.group(1).split(',') if name.strip()}
route_cases = set(re.findall(r'Screen\.(\w+)\s*->\s*[A-Z]\w+Screen\s*\(', main))
# Explicit navigation cases in NativeShell can wrap onto several lines.
shell = main.split('private fun NativeShell(',1)[1].split('\n@Composable\nprivate fun HomeScreen(',1)[0]
route_cases = set(re.findall(r'Screen\.(\w+)\s*->', shell))
more = main.split('private fun MoreScreen(',1)[1].split('\n@Composable\nprivate fun SimpleApiListScreen(',1)[0]
entries = re.findall(r'MenuFeature\("([^"]+)",\s*Icons\.Default\.\w+,\s*Screen\.(\w+)', more)
failed = []
for label, dest in entries:
    if dest not in valid: failed.append(f'{label} -> Screen.{dest} not declared')
    if dest not in route_cases: failed.append(f'{label} -> Screen.{dest} not routed')
if len(entries) != 26:
    failed.append(f'Expected 26 More-menu entries, found {len(entries)}')
nav_required = ['Screen.Home', 'Screen.Classes', 'Screen.Exams', 'Screen.Reports', 'Screen.More']
nav_section = main.split('private fun TeacherBottomBar(', 1)[1].split('private fun NativeShell(', 1)[0] if 'private fun TeacherBottomBar(' in main else ''
for route in nav_required:
    if route not in nav_section:
        failed.append(f'Missing primary bottom navigation item: {route}')
noops = []
for p in sources:
    for m in re.finditer(r'onClick\s*=\s*\{\s*}', p.read_text()):
        noops.append(f'{p.name}: {p.read_text()[:m.start()].count(chr(10))+1}')
if noops: failed.append('No-op click handlers: '+', '.join(noops))
required = {
    'class-message deep link': 'onMessageStudent(studentId, subject)',
    'contact scope check': 'findContactForTarget(contacts, messageTarget)',
    'bank exam-copy edit': 'onOpenExamStudio(q.optString("id"))',
    'exam detail selection': 'selectedId = exam.optString("id")',
    'account password action': 'AccountChangePasswordDialog(',
}
combined = '\n'.join(p.read_text() for p in sources)
for title, token in required.items():
    if token not in combined: failed.append('Missing action: '+title)
if failed:
    print('FAIL:')
    print('\n'.join(' - '+x for x in failed))
    sys.exit(1)
print('PASS: all 26 More routes point to a declared, dispatched screen')
print('PASS: five primary bottom navigation destinations are present in the custom visual bar')
print('PASS: no literal onClick={} handlers in Kotlin UI files')
print('PASS: class chat, scoped contact, bank editor, exam details, account password routes present')
print('NOTE: this is static. API and on-device interaction are NOT verified.')
