import { useState } from 'react';
import { getTeamIdentity } from '../utils/teamIdentity';

type CrestSize = 'sm' | 'md' | 'lg';

interface TeamCrestProps {
  name: string;
  size?: CrestSize;
  className?: string;
  /** Overrides the auto-resolved crest image (rarely needed — identity already resolves one). */
  imageUrl?: string;
}

type CrestCSSVars = React.CSSProperties & {
  '--crest-from'?: string;
  '--crest-to'?: string;
  '--crest-glow'?: string;
};

export default function TeamCrest({ name, size = 'md', className = '', imageUrl }: TeamCrestProps) {
  const identity = getTeamIdentity(name);
  const [imageFailed, setImageFailed] = useState(false);
  const resolvedImage = imageUrl ?? identity.imageUrl;
  const showImage = !!resolvedImage && !imageFailed;

  const style: CrestCSSVars = {
    '--crest-from': identity.gradientFrom,
    '--crest-to': identity.gradientTo,
    '--crest-glow': identity.glow,
  };

  return (
    <span
      className={`team-crest team-crest-${size} ${showImage ? '' : 'team-crest-badge'} ${className}`.trim()}
      style={style}
      title={name}
      aria-label={name}
      role="img"
    >
      {showImage ? (
        <img
          src={resolvedImage}
          alt={name}
          className="team-crest-image"
          loading="lazy"
          onError={() => setImageFailed(true)}
        />
      ) : (
        <span className="team-crest-initials">{identity.initials}</span>
      )}
    </span>
  );
}
