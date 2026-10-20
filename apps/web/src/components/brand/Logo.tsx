"use client";

import { useId } from "react";

/**
 * The ProofU mark and wordmark (docs/design/brand.md §로고). Both draw in `currentColor`, so the
 * parent's text color picks the variant: primary-600 on light surfaces, white on primary-800.
 * The wordmark's U tab is cut with a mask, so it is a client component only for `useId`: two
 * copies on one page (sidebar and phone top bar) must not share a mask id, and a mask inside a
 * `display:none` copy would blank out the other.
 */

/** 인용 괄호 안의 확인된 사람: citation brackets around a head and a check-mark body. 120 grid. */
export function BrandMark({ size = 32, className }: { size?: number; className?: string }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="6 6 108 108"
      className={className}
      aria-hidden
      focusable="false"
    >
      <g fill="none" stroke="currentColor" strokeWidth="11">
        <path d="M36 14H18V106H36" />
        <path d="M84 14H102V106H84" />
      </g>
      <circle cx="60" cy="38" r="9.5" fill="currentColor" />
      <polyline
        points="42,64 56,78 80,54"
        fill="none"
        stroke="currentColor"
        strokeWidth="11"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

/** The ProofU wordmark: two record layers in the P, a folded document tab on the U. */
export function Wordmark({ width = 104, className }: { width?: number; className?: string }) {
  const tab = useId();
  return (
    <svg
      width={width}
      height={(width * 120) / 470}
      viewBox="-6 -14 470 120"
      className={className}
      aria-hidden
      focusable="false"
    >
      <mask id={tab} maskUnits="userSpaceOnUse" x="360" y="-30" width="120" height="150">
        <rect x="360" y="-30" width="120" height="150" fill="#fff" />
        <polygon points="443,-2 462,-2 462,17" fill="#000" />
        <polyline points="445,-2 445,13 462,13" fill="none" stroke="#000" strokeWidth="2.6" />
      </mask>
      <path
        fill="currentColor"
        fillRule="evenodd"
        d="M0 0H48A32 32 0 0 1 48 64H18V100H0Z M18 18V46H48A14 14 0 0 0 48 18Z"
      />
      <rect x="24" y="23" width="30" height="5" fill="currentColor" />
      <rect x="24" y="35" width="22" height="5" fill="currentColor" />
      <g fill="none" stroke="currentColor" strokeWidth="17">
        <path d="M100.5 100V58A30 30 0 0 1 130.5 28H136" />
        <circle cx="182" cy="64" r="27.5" />
        <circle cx="264" cy="64" r="27.5" />
        <path d="M320.5 100V26A24 24 0 0 1 344.5 2H352" />
        <path d="M304 36.5H348" />
      </g>
      <path
        mask={`url(#${tab})`}
        fill="currentColor"
        d="M368 0H386V55A27 27 0 0 0 440 55V0H458V55A45 45 0 0 1 368 55Z"
      />
    </svg>
  );
}

/**
 * 가로 조합형: the mark at 1.2× the wordmark's height with a quarter of its width between them.
 * Decorative by itself; the link or heading around it carries the name.
 */
export function BrandLockup({ height = 28, className }: { height?: number; className?: string }) {
  const wordmarkWidth = (height / 1.2) * (470 / 120);
  return (
    <span className={["inline-flex items-center", className].filter(Boolean).join(" ")}>
      <BrandMark size={height} />
      <span style={{ width: height * 0.25 }} aria-hidden />
      <Wordmark width={wordmarkWidth} />
    </span>
  );
}
