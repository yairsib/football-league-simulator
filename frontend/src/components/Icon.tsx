export type IconName =
  | 'goal'
  | 'redCard'
  | 'substitution'
  | 'trophy'
  | 'target'
  | 'crown'
  | 'chevronDown'
  | 'lock'
  | 'unlock'
  | 'bolt'
  | 'shield'
  | 'weatherClear'
  | 'weatherRain'
  | 'weatherWind'
  | 'weatherHot'
  | 'weatherCold'
  | 'weatherStorm';

const WEATHER_ICON_MAP: Record<string, IconName> = {
  CLEAR: 'weatherClear',
  RAIN: 'weatherRain',
  WIND: 'weatherWind',
  HOT: 'weatherHot',
  COLD: 'weatherCold',
  STORM: 'weatherStorm',
};

export function weatherIconName(condition: string): IconName {
  return WEATHER_ICON_MAP[condition] ?? 'weatherClear';
}

interface IconProps {
  name: IconName;
  size?: number;
  className?: string;
  style?: React.CSSProperties;
}

// Small inline stroke-style icon set — kept deliberately simple/consistent
// so match events, dashboard highlights, and status pills don't rely on
// mismatched system emoji rendering across platforms.
export default function Icon({ name, size = 16, className = '', style }: IconProps) {
  const common = {
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 2,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    className: `icon icon-${name} ${className}`.trim(),
    style,
    'aria-hidden': true,
  };

  switch (name) {
    case 'goal':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="9" />
          <path d="M12 6.5 15.8 9.2 14.4 13.8H9.6L8.2 9.2Z" />
          <path d="M12 6.5V3.5M15.8 9.2 19 7M14.4 13.8 16.5 17.5M9.6 13.8 7.5 17.5M8.2 9.2 5 7" />
        </svg>
      );
    case 'redCard':
      return (
        <svg {...common} fill="currentColor" stroke="none">
          <rect x="6" y="3" width="12" height="18" rx="2" transform="rotate(-8 12 12)" />
        </svg>
      );
    case 'substitution':
      return (
        <svg {...common}>
          <path d="M7 7h11l-3-3M17 17H6l3 3" />
        </svg>
      );
    case 'trophy':
      return (
        <svg {...common}>
          <path d="M7 4h10v4a5 5 0 0 1-5 5 5 5 0 0 1-5-5Z" />
          <path d="M7 5H4v1a4 4 0 0 0 4 4M17 5h3v1a4 4 0 0 1-4 4" />
          <path d="M12 13v3M9 20h6M9.5 20c0-2 1-2.5 2.5-2.5s2.5.5 2.5 2.5" />
        </svg>
      );
    case 'target':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="8" />
          <circle cx="12" cy="12" r="4.2" />
          <circle cx="12" cy="12" r="0.8" fill="currentColor" />
        </svg>
      );
    case 'crown':
      return (
        <svg {...common}>
          <path d="M4 18h16l-1.5-9-4 3.5L12 6l-2.5 6.5-4-3.5Z" />
          <path d="M6 21h12" />
        </svg>
      );
    case 'chevronDown':
      return (
        <svg {...common}>
          <path d="M6 9l6 6 6-6" />
        </svg>
      );
    case 'lock':
      return (
        <svg {...common}>
          <rect x="5" y="11" width="14" height="9" rx="1.5" />
          <path d="M8 11V7a4 4 0 0 1 8 0v4" />
        </svg>
      );
    case 'unlock':
      return (
        <svg {...common}>
          <rect x="5" y="11" width="14" height="9" rx="1.5" />
          <path d="M8 11V7a4 4 0 0 1 7.5-2" />
        </svg>
      );
    case 'bolt':
      return (
        <svg {...common} fill="currentColor" stroke="none">
          <path d="M13 2 4 14h6l-1 8 9-12h-6Z" />
        </svg>
      );
    case 'shield':
      return (
        <svg {...common}>
          <path d="M12 3 5 6v5c0 4.5 3 8 7 10 4-2 7-5.5 7-10V6Z" />
        </svg>
      );
    case 'weatherClear':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="4.5" />
          <path d="M12 3v2M12 19v2M4.2 4.2l1.4 1.4M18.4 18.4l1.4 1.4M3 12h2M19 12h2M4.2 19.8l1.4-1.4M18.4 5.6l1.4-1.4" />
        </svg>
      );
    case 'weatherRain':
      return (
        <svg {...common}>
          <path d="M7 15.5A4.5 4.5 0 0 1 8 6.6 5.5 5.5 0 0 1 18.6 8 4 4 0 0 1 18 15.5H7Z" />
          <path d="M9 19l-1 2M13 19l-1 2M17 19l-1 2" />
        </svg>
      );
    case 'weatherWind':
      return (
        <svg {...common}>
          <path d="M3 8h11a2.5 2.5 0 1 0-2.5-2.5" />
          <path d="M3 12h15a2.5 2.5 0 1 1-2.5 2.5" />
          <path d="M3 16h9a2 2 0 1 1-2 2" />
        </svg>
      );
    case 'weatherHot':
      return (
        <svg {...common}>
          <circle cx="12" cy="10" r="4" />
          <path d="M12 3v1.5M18.4 5.6l-1 1M5.6 5.6l1 1" />
          <path d="M4 19c1.5-1.5 3-1.5 4.5 0s3 1.5 4.5 0 3-1.5 4.5 0 3 1.5 4.5 0" />
        </svg>
      );
    case 'weatherCold':
      return (
        <svg {...common}>
          <path d="M12 3v18M5 6.5l14 11M19 6.5 5 17.5" />
          <path d="M12 3l-2 2M12 3l2 2M12 21l-2-2M12 21l2-2M5 6.5l2.7-.4M5 6.5l.6 2.7M19 6.5l-2.7-.4M19 6.5l-.6 2.7M5 17.5l.6-2.7M5 17.5l2.7.4M19 17.5l-.6-2.7M19 17.5l-2.7.4" />
        </svg>
      );
    case 'weatherStorm':
      return (
        <svg {...common}>
          <path d="M7 13.5A4.5 4.5 0 0 1 8 4.6 5.5 5.5 0 0 1 18.6 6 4 4 0 0 1 18 13.5H7Z" />
          <path d="M13 13l-2.5 4h3L11 21" />
        </svg>
      );
    default:
      return null;
  }
}
