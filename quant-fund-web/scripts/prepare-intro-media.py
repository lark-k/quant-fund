"""Derive silent web assets from the two approved originals; never overwrite sources."""
from pathlib import Path
import json
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'qa-artifacts/intro-art-direction/source'
DEST = ROOT / 'quant-fund-web/public/intro/flower-v1'


def ffmpeg(*args):
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', *map(str, args)], check=True)


for name, width in [('desktop', 1280), ('mobile', 768)]:
    folder = DEST / name
    folder.mkdir(parents=True, exist_ok=True)
    # Every original frame is retained in the active opening/closing interval.
    ffmpeg('-i', SOURCE / '花朵开合母版.mp4', '-vf',
           f"select='between(n,36,204)',scale={width}:-2", '-fps_mode', 'vfr',
           '-start_number', '0', '-c:v', 'libwebp', '-quality', '82',
           '-compression_level', '5', folder / '%03d.webp')

ffmpeg('-i', SOURCE / '穿过花心的进入转场.mp4', '-an',
       '-vf', 'setpts=0.5*PTS', '-r', '48', '-c:v', 'libx264', '-preset', 'slow',
       '-crf', '21', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', DEST / 'enter.mp4')
ffmpeg('-i', SOURCE / '穿过花心的进入转场.mp4', '-frames:v', '1',
       '-c:v', 'libwebp', '-quality', '86', DEST / 'enter-poster.webp')
(DEST / 'manifest.json').write_text(json.dumps({
    'frames': 169, 'firstSourceFrame': 36, 'lastSourceFrame': 204,
    'sourceFps': 24, 'frameZero': 'open', 'lastFrame': 'closed',
    'variants': {'desktop': [1280, 720], 'mobile': [768, 432]},
    'entry': 'enter.mp4', 'entrySpeed': 2, 'audio': False,
}, indent=2) + '\n', encoding='utf-8')
print('Prepared intro assets:', DEST)
print('Total bytes:', sum(p.stat().st_size for p in DEST.rglob('*') if p.is_file()))
