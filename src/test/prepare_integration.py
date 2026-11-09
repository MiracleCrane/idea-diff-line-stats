import pathlib, json, subprocess, zipfile, shutil, argparse

parser = argparse.ArgumentParser()
parser.add_argument('--idea-home', required=True)
parser.add_argument('--build-dir', required=True)
args_in = parser.parse_args()
base = pathlib.Path(args_in.build_dir).resolve()
ide = pathlib.Path(args_in.idea_home).resolve()
sandbox = base / 'sandbox'
repo = base / 'fixture'
repo.mkdir(parents=True, exist_ok=True)
def git(*args):
    subprocess.run(['git', '-C', str(repo), *args], check=True, capture_output=True)
if not (repo / '.git').exists():
    git('init', '-b', 'baseline')
    git('config', 'user.name', 'Diff Stats Test')
    git('config', 'user.email', 'test@example.invalid')
    git('config', 'core.autocrlf', 'false')
    (repo / 'Modified.txt').write_text('same\nold\n', encoding='utf8')
    (repo / 'Deleted.txt').write_text('one\ntwo\n', encoding='utf8')
    (repo / 'Rename.txt').write_text('unchanged\n', encoding='utf8')
    git('add', '.')
    git('commit', '-m', 'baseline fixture')
    git('switch', '-c', 'feature')
    (repo / 'Modified.txt').write_text('same\nnew\nextra\n', encoding='utf8')
    (repo / 'Added.txt').write_text('one\ntwo\nthree\n', encoding='utf8')
    nested = repo / 'deep/path'
    nested.mkdir(parents=True)
    (nested / 'VeryLongFileNameForCheckingStatisticsInNarrowPanels.txt').write_text('prefix visibility fixture\nunique new content\n', encoding='utf8')
    (repo / 'image.png').write_bytes(b'\x89PNG\r\n\x1a\n\x00binary')
    git('rm', 'Deleted.txt')
    git('mv', 'Rename.txt', 'Renamed.txt')
    git('add', '.')
    git('commit', '-m', 'feature fixture')
plugins = sandbox / 'plugins'
plugins.mkdir(parents=True, exist_ok=True)
shutil.copytree(base / 'package/diff-line-stats', plugins / 'diff-line-stats', dirs_exist_ok=True)
testlib = plugins / 'integration-test/lib'
testlib.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(testlib / 'integration-test.jar', 'w', zipfile.ZIP_DEFLATED) as z:
    z.writestr('META-INF/plugin.xml', '''<idea-plugin>
<id>dev.miraclecrane.diffstats.tests</id><name>Diff Stats Integration Tests</name><version>1</version>
<vendor>Local Test</vendor><idea-version since-build="263.7307" until-build="263.*"/>
<depends>dev.miraclecrane.diff-line-stats</depends><depends>Git4Idea</depends>
<extensions defaultExtensionNs="com.intellij"><appStarter id="diffStatsTest" implementation="dev.miraclecrane.diffstats.IntegrationStarter"/></extensions>
</idea-plugin>''')
    for p in (base / 'test-classes').rglob('*.class'):
        z.write(p, p.relative_to(base / 'test-classes').as_posix())
launch = json.loads((ide / 'product-info.json').read_text())['launch'][0]
args = ['-Xmx1500m', '-Djava.awt.headless=true', '-Didea.is.internal=true',
        '-Didea.home.path=' + str(ide), '-Didea.config.path=' + str(sandbox / 'config'),
        '-Didea.system.path=' + str(sandbox / 'system'), '-Didea.plugins.path=' + str(plugins),
        '-Didea.log.path=' + str(sandbox / 'log'), '-Didea.trust.all.projects=true',
        '-Ddiffstats.test.repo=' + str(repo), '-Ddiffstats.test.image=' + str(base / 'diff-line-stats-preview.png')]
args += [a.replace('%IDE_HOME%', str(ide)) for a in launch.get('additionalJvmArguments', [])]
args += ['-Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider']
args += ['-cp', ';'.join(str(ide / 'lib' / n) for n in launch['bootClassPathJarNames']), 'com.intellij.idea.Main', 'diffStatsTest']
(base / 'idea-test.args').write_text('\n'.join('"' + a.replace('\\', '/') + '"' for a in args), encoding='utf8')
print('Prepared isolated IDE sandbox and Git fixture')
