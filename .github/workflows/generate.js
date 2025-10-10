const fs = require('fs');
const path = require('path');
const propReader = require('properties-reader');

const repoRoot = process.cwd();
const pluginsDir = path.join(repoRoot, 'plugins', 'v126');
const docsDir = path.join(repoRoot, 'docs');
const outputMdFile = path.join(docsDir, 'index.md');
const outputJsonFile = path.join(docsDir, 'index.json');

function isValidPlugin(pluginPath) {
    try {
        const files = new Set(fs.readdirSync(pluginPath));
        return files.has('info.prop') && files.has('main.java') && files.has('readme.md');
    } catch {
        return false;
    }
}

function parseInfoProp(filePath) {
    try {
        const props = propReader(filePath);
        return {
            name: props.get('name'),
            author: props.get('author'),
            version: props.get('version'),
            updateTime: props.get('updateTime'),
        };
    } catch {
        return {
            name: '未知插件',
            author: '佚名',
            version: '1.0.0',
            updateTime: '19700101',
        };
    }
}

function getPluginInfo(pluginPath) {
    const rel = path.relative(repoRoot, pluginPath).replace(/\\/g, '/');
    const homeLink = `https://github.com/HdShare/WAuxiliary_Plugin/tree/main/${rel}`;
    const props = parseInfoProp(path.join(pluginPath, 'info.prop'));
    return {
        name: props.name,
        author: props.author,
        version: props.version,
        updateTime: props.updateTime,
        homeLink: homeLink,
        downloadUrl: `https://minhaskamal.github.io/DownGit/#/home?url=${homeLink}`,
    };
}

function traversePlugins(pluginDir) {
    const authors = fs.readdirSync(pluginDir).filter(sub => fs.statSync(path.join(pluginDir, sub)).isDirectory());
    const plugins = authors.flatMap(authorName => {
        const authorPath = path.join(pluginDir, authorName);
        return fs.readdirSync(authorPath)
            .filter(sub => fs.statSync(path.join(authorPath, sub)).isDirectory())
            .map(pluginName => {
                const pluginPath = path.join(authorPath, pluginName);
                return isValidPlugin(pluginPath) ? getPluginInfo(pluginPath) : null;
            })
            .filter(Boolean);
    });
    return plugins.sort((a, b) => {
        const timeA = parseInt(a.updateTime);
        const timeB = parseInt(b.updateTime);
        return timeB - timeA;
    });
}

function generateMarkdown(plugins) {
    let md = `---\nlayout: home\n\nhero:\n  name: "WAuxiliary Plugin"\n  text: "WAuxiliary 插件"\n\nfeatures:\n`;
    plugins.forEach(plugin => {
        md += `  - title: ${plugin.name}@${plugin.author}\n    details: 版本 ${plugin.version} | 更新于 ${plugin.updateTime}\n    link: ${plugin.downloadUrl}\n\n`;
    });
    return md;
}

function generateJSON(plugins) {
    const jsonData = {
        generatedAt: new Date().toISOString().split('T')[0],
        totalPlugins: plugins.length,
        plugins: plugins.map(plugin => ({
            name: plugin.name,
            author: plugin.author,
            version: plugin.version,
            updateTime: plugin.updateTime,
            homeLink: plugin.homeLink,
            downloadUrl: plugin.downloadUrl,
        }))
    };
    return JSON.stringify(jsonData, null, 2);
}

const plugins = traversePlugins(pluginsDir);
console.log(`正在处理 ${plugins.length} 个插件`);

const markdown = generateMarkdown(plugins);
fs.writeFileSync(outputMdFile, markdown, 'utf8');
console.log(`已自动生成 ${outputMdFile}`);

const jsonData = generateJSON(plugins);
fs.writeFileSync(outputJsonFile, jsonData, 'utf8');
console.log(`已自动生成 ${outputJsonFile}`);
