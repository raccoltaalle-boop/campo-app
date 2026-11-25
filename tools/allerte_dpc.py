#!/usr/bin/env python3
"""Scarica l'ultimo bollettino di criticità della Protezione Civile (pcm-dpc su GitHub)
e produce allerte.json: zone di allerta semplificate con i livelli di oggi e domani.
Usato da .github/workflows/allerte.yml; l'app lo legge dal branch "allerte"."""
import json, os, sys, urllib.request

REPO = 'pcm-dpc/DPC-Bollettini-Criticita-Idrogeologica-Idraulica'
RAW = f'https://raw.githubusercontent.com/{REPO}/master/'
API = f'https://api.github.com/repos/{REPO}'
TOKEN = os.environ.get('GITHUB_TOKEN', '')
TYPES = ['Per rischio idraulico', 'Per rischio idrogeologico', 'Per rischio temporali']


def get(url, api=False):
    req = urllib.request.Request(url, headers={'User-Agent': 'campo-app'})
    if api and TOKEN:
        req.add_header('Authorization', 'Bearer ' + TOKEN)
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()


def level(s):
    s = (s or '').upper()
    return 3 if 'ROSSA' in s else 2 if 'ARANCIONE' in s else 1 if 'GIALLA' in s else 0


def dp(pts, tol):
    """Douglas-Peucker."""
    if len(pts) < 4:
        return pts
    (x1, y1), (x2, y2) = pts[0], pts[-1]
    dx, dy = x2 - x1, y2 - y1
    n = (dx * dx + dy * dy) ** .5 or 1e-12
    imax, dmax = 0, 0
    for i in range(1, len(pts) - 1):
        x, y = pts[i]
        d = abs(dy * x - dx * y + x2 * y1 - y2 * x1) / n
        if d > dmax:
            imax, dmax = i, d
    if dmax <= tol:
        return [pts[0], pts[-1]]
    return dp(pts[:imax + 1], tol)[:-1] + dp(pts[imax:], tol)


def simplify(geom, tol=0.006):
    polys = geom['coordinates'] if geom['type'] == 'MultiPolygon' else [geom['coordinates']]
    out = []
    for poly in polys:
        rings = []
        for ring in poly:
            pts = [(round(x, 3), round(y, 3)) for x, y in ring]
            m = len(pts) // 2
            r = dp(pts[:m + 1], tol)[:-1] + dp(pts[m:], tol)  # anello chiuso: due metà
            if len(r) >= 4:
                rings.append([[x, y] for x, y in r])
        if rings:
            out.append(rings)
    return out


def main(out_path):
    # ultimo bollettino: file files/AAAAMMGG_HHMM.json più recente
    tree = json.loads(get(API + '/git/trees/master', api=True))
    files_sha = next(t['sha'] for t in tree['tree'] if t['path'] == 'files')
    files = json.loads(get(API + '/git/trees/' + files_sha, api=True))['tree']
    names = sorted(t['path'] for t in files if t['path'].endswith('.json') and t['path'][:8].isdigit())
    latest = names[-1]
    stem = latest[:-5]
    index = json.loads(get(RAW + 'files/' + latest))
    days = {}
    for key in ('today', 'tomorrow'):
        days[key] = json.loads(get(RAW + f'files/geojson/{stem}_{key}.json'))['features']

    zones = {}
    for key, feats in days.items():
        for f in feats:
            p = f['properties']
            name = p.get('Nome zona', '?')
            z = zones.setdefault(name, {'n': name})
            z['t' if key == 'today' else 'd'] = [level(p.get(t)) for t in TYPES]
            if 'p' not in z:
                z['p'] = simplify(f['geometry'])
                xs = [pt[0] for poly in z['p'] for ring in poly for pt in ring]
                ys = [pt[1] for poly in z['p'] for ring in poly for pt in ring]
                z['b'] = [min(xs), min(ys), max(xs), max(ys)] if xs else [0, 0, 0, 0]
    data = {
        'source': 'Dipartimento della Protezione Civile — bollettino di criticità idrogeologica e idraulica',
        'name': index.get('name', stem),
        'issued': index.get('date', ''),
        'file': latest,
        'types': ['idraulico', 'idrogeologico', 'temporali'],
        'zones': list(zones.values()),
    }
    with open(out_path, 'w', encoding='utf-8') as fo:
        json.dump(data, fo, ensure_ascii=False, separators=(',', ':'))
    print(data['name'], len(data['zones']), 'zone', os.path.getsize(out_path) // 1024, 'KB')


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'allerte.json')
