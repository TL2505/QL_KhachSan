const fs = require('fs');
const path = require('path');

const publicDir = path.resolve('web-ui/public');
const visited = new Set();
const missing = [];

function checkFile(filePath) {
  if (visited.has(filePath)) return;
  visited.add(filePath);
  if (!fs.existsSync(filePath)) {
    missing.push(filePath);
    return;
  }
  const content = fs.readFileSync(filePath, 'utf8');
  const importRegex = /from\s+['"]([^'"]+)['"]/g;
  let match;
  while ((match = importRegex.exec(content)) !== null) {
    let importPath = match[1].split('?')[0];
    let resolved = path.resolve(path.dirname(filePath), importPath);
    checkFile(resolved);
  }
  const bareImportRegex = /import\s+['"]([^'"]+)['"]/g;
  while ((match = bareImportRegex.exec(content)) !== null) {
    let importPath = match[1].split('?')[0];
    let resolved = path.resolve(path.dirname(filePath), importPath);
    checkFile(resolved);
  }
}

checkFile(path.join(publicDir, 'portal-app.js'));
console.log('Total files checked in dependency graph:', visited.size);
console.log('Missing files:', missing);
