// Club identity — real crest images first, gradient/initials badge as fallback
// (used automatically whenever a team has no image, or its image fails to load).
// See docs/CREST_SOURCES.md for where each crest image came from.

export interface TeamIdentity {
  initials: string;
  gradientFrom: string;
  gradientTo: string;
  glow: string;
  imageUrl?: string;
}

// Real crest images for every team currently in the league data — served from
// frontend/public/crests/ (Vite serves /public as-is, no bundling needed).
const CREST_IMAGE: Record<string, string> = {
  'maccabi tel aviv': '/crests/maccabi-tel-aviv.png',
  'hapoel beer sheva': '/crests/hapoel-beer-sheva.png',
  'beitar jerusalem': '/crests/beitar-jerusalem.svg',
  'maccabi haifa': '/crests/maccabi-haifa.png',
  'hapoel tel aviv': '/crests/hapoel-tel-aviv.png',
  'hapoel petah tikva': '/crests/hapoel-petah-tikva.png',
  'maccabi netanya': '/crests/maccabi-netanya.webp',
  'bnei sakhnin': '/crests/bnei-sakhnin.svg',
  'ironi kiryat shmona': '/crests/ironi-kiryat-shmona.png',
  'hapoel haifa': '/crests/hapoel-haifa.png',
  'fc ashdod': '/crests/fc-ashdod.svg',
  'hapoel jerusalem': '/crests/hapoel-jerusalem.png',
  'ironi tiberias': '/crests/ironi-tiberias.png',
  'maccabi bnei reineh': '/crests/maccabi-bnei-reineh.png',
};

interface PaletteEntry {
  from: string;
  to: string;
  glow: string;
}

// 14 curated, visually distinct gradients — enough for every real Ligat Ha'Al team
// to get its own identity, with a hash-based fallback for any other team name.
const PALETTE: PaletteEntry[] = [
  { from: '#10b981', to: '#047857', glow: 'rgba(16, 185, 129, 0.55)' },
  { from: '#f59e0b', to: '#b45309', glow: 'rgba(245, 158, 11, 0.55)' },
  { from: '#3b82f6', to: '#1d4ed8', glow: 'rgba(59, 130, 246, 0.55)' },
  { from: '#ef4444', to: '#991b1b', glow: 'rgba(239, 68, 68, 0.55)' },
  { from: '#8b5cf6', to: '#5b21b6', glow: 'rgba(139, 92, 246, 0.55)' },
  { from: '#06b6d4', to: '#0e7490', glow: 'rgba(6, 182, 212, 0.55)' },
  { from: '#f43f5e', to: '#9f1239', glow: 'rgba(244, 63, 94, 0.55)' },
  { from: '#fb923c', to: '#c2410c', glow: 'rgba(251, 146, 60, 0.55)' },
  { from: '#6366f1', to: '#3730a3', glow: 'rgba(99, 102, 241, 0.55)' },
  { from: '#84cc16', to: '#3f6212', glow: 'rgba(132, 204, 22, 0.55)' },
  { from: '#94a3b8', to: '#334155', glow: 'rgba(148, 163, 184, 0.45)' },
  { from: '#d946ef', to: '#86198f', glow: 'rgba(217, 70, 239, 0.55)' },
  { from: '#14b8a6', to: '#0f766e', glow: 'rgba(20, 184, 166, 0.55)' },
  { from: '#eab308', to: '#854d0e', glow: 'rgba(234, 179, 8, 0.55)' },
];

// Explicit assignment for the real 2025/2026 Ligat Ha'Al teams so each one keeps
// a stable, unique identity (not just whatever a name hash happens to produce).
const TEAM_PALETTE_INDEX: Record<string, number> = {
  'maccabi tel aviv': 0,
  'hapoel beer sheva': 1,
  'beitar jerusalem': 2,
  'maccabi haifa': 3,
  'hapoel tel aviv': 4,
  'hapoel petah tikva': 5,
  'maccabi netanya': 6,
  'bnei sakhnin': 7,
  'ironi kiryat shmona': 8,
  'hapoel haifa': 9,
  'fc ashdod': 10,
  'hapoel jerusalem': 11,
  'ironi tiberias': 12,
  'maccabi bnei reineh': 13,
};

function hashString(value: string): number {
  let hash = 0;
  for (let i = 0; i < value.length; i++) {
    hash = (hash * 31 + value.charCodeAt(i)) >>> 0;
  }
  return hash;
}

function computeInitials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '?';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}

export function getTeamIdentity(name: string | null | undefined): TeamIdentity {
  const safeName = (name ?? '').trim();
  if (!safeName) {
    return { initials: '?', gradientFrom: '#334155', gradientTo: '#1e293b', glow: 'rgba(148,163,184,0.3)' };
  }
  const key = safeName.toLowerCase();
  const index = key in TEAM_PALETTE_INDEX ? TEAM_PALETTE_INDEX[key] : hashString(key) % PALETTE.length;
  const palette = PALETTE[index];
  return {
    initials: computeInitials(safeName),
    gradientFrom: palette.from,
    gradientTo: palette.to,
    glow: palette.glow,
    imageUrl: CREST_IMAGE[key],
  };
}
