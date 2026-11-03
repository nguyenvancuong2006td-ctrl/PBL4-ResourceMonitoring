interface TuxMascotProps {
  className?: string;
}

// Chim cánh cụt Tux vẽ bằng SVG, có viền neon xanh
export default function TuxMascot({ className }: TuxMascotProps) {
  return (
    <svg viewBox="0 0 200 250" className={className} role="img" aria-label="Tux">
      <defs>
        <linearGradient id="tuxBody" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#2563eb" />
          <stop offset="1" stopColor="#0b1b4d" />
        </linearGradient>
        <linearGradient id="tuxWing" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#1e40af" />
          <stop offset="1" stopColor="#0a1640" />
        </linearGradient>
        <linearGradient id="tuxBelly" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#ffffff" />
          <stop offset="1" stopColor="#bfdbfe" />
        </linearGradient>
        <linearGradient id="tuxBeak" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#fde047" />
          <stop offset="1" stopColor="#f59e0b" />
        </linearGradient>
        <filter id="tuxGlow" x="-30%" y="-30%" width="160%" height="160%">
          <feGaussianBlur stdDeviation="4" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      <g filter="url(#tuxGlow)">
        {/* Chân */}
        <ellipse cx="72" cy="233" rx="27" ry="10" fill="url(#tuxBeak)" transform="rotate(-8 72 233)" />
        <ellipse cx="128" cy="233" rx="27" ry="10" fill="url(#tuxBeak)" transform="rotate(8 128 233)" />

        {/* Cánh */}
        <path
          d="M44 118 C14 150 18 196 42 212 C58 184 58 150 54 116 Z"
          fill="url(#tuxWing)" stroke="#38bdf8" strokeWidth="2"
        />
        <path
          d="M156 118 C186 150 182 196 158 212 C142 184 142 150 146 116 Z"
          fill="url(#tuxWing)" stroke="#38bdf8" strokeWidth="2"
        />

        {/* Thân */}
        <path
          d="M100 14 C62 14 46 50 46 92 C46 122 30 152 34 188 C38 220 66 238 100 238 C134 238 162 220 166 188 C170 152 154 122 154 92 C154 50 138 14 100 14 Z"
          fill="url(#tuxBody)" stroke="#38bdf8" strokeWidth="2.5"
        />

        {/* Bụng */}
        <path
          d="M100 100 C72 100 58 132 58 164 C58 202 78 228 100 228 C122 228 142 202 142 164 C142 132 128 100 100 100 Z"
          fill="url(#tuxBelly)"
        />

        {/* Mặt */}
        <path
          d="M100 36 C78 36 64 56 64 78 C64 98 82 112 100 112 C118 112 136 98 136 78 C136 56 122 36 100 36 Z"
          fill="#e0f2fe"
        />

        {/* Mắt */}
        <ellipse cx="84" cy="68" rx="9" ry="12" fill="#ffffff" stroke="#0b1b4d" strokeWidth="1.5" />
        <ellipse cx="116" cy="68" rx="9" ry="12" fill="#ffffff" stroke="#0b1b4d" strokeWidth="1.5" />
        <circle cx="86" cy="71" r="4.5" fill="#0b1b4d" />
        <circle cx="114" cy="71" r="4.5" fill="#0b1b4d" />
        <circle cx="87.5" cy="69" r="1.4" fill="#ffffff" />
        <circle cx="115.5" cy="69" r="1.4" fill="#ffffff" />

        {/* Mỏ */}
        <path
          d="M78 90 C78 80 122 80 122 90 C122 102 111 111 100 111 C89 111 78 102 78 90 Z"
          fill="url(#tuxBeak)"
        />
        <path d="M82 92 C94 97 106 97 118 92" stroke="#b45309" strokeWidth="1.6" fill="none" strokeLinecap="round" />
      </g>
    </svg>
  );
}