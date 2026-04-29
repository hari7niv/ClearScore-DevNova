---
name: Professional AI Loan Assurance
colors:
  surface: '#131315'
  surface-dim: '#131315'
  surface-bright: '#39393b'
  surface-container-lowest: '#0e0e10'
  surface-container-low: '#1b1b1d'
  surface-container: '#1f1f21'
  surface-container-high: '#2a2a2b'
  surface-container-highest: '#353436'
  on-surface: '#e4e2e4'
  on-surface-variant: '#c6c6cd'
  inverse-surface: '#e4e2e4'
  inverse-on-surface: '#303032'
  outline: '#909097'
  outline-variant: '#45464d'
  surface-tint: '#bec6e0'
  primary: '#bec6e0'
  on-primary: '#283044'
  primary-container: '#0f172a'
  on-primary-container: '#798098'
  inverse-primary: '#565e74'
  secondary: '#bcc7de'
  on-secondary: '#263143'
  secondary-container: '#3e495d'
  on-secondary-container: '#aeb9d0'
  tertiary: '#dec29a'
  on-tertiary: '#3e2d11'
  tertiary-container: '#231500'
  on-tertiary-container: '#957d5a'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#dae2fd'
  primary-fixed-dim: '#bec6e0'
  on-primary-fixed: '#131b2e'
  on-primary-fixed-variant: '#3f465c'
  secondary-fixed: '#d8e3fb'
  secondary-fixed-dim: '#bcc7de'
  on-secondary-fixed: '#111c2d'
  on-secondary-fixed-variant: '#3c475a'
  tertiary-fixed: '#fcdeb5'
  tertiary-fixed-dim: '#dec29a'
  on-tertiary-fixed: '#271901'
  on-tertiary-fixed-variant: '#574425'
  background: '#131315'
  on-background: '#e4e2e4'
  surface-variant: '#353436'
typography:
  h1:
    fontFamily: Manrope
    fontSize: 40px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: -0.02em
  h2:
    fontFamily: Manrope
    fontSize: 32px
    fontWeight: '600'
    lineHeight: '1.3'
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Manrope
    fontSize: 18px
    fontWeight: '400'
    lineHeight: '1.6'
  body-md:
    fontFamily: Manrope
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.6'
  input-text:
    fontFamily: Manrope
    fontSize: 20px
    fontWeight: '500'
    lineHeight: '1.4'
  label-caps:
    fontFamily: Manrope
    fontSize: 12px
    fontWeight: '700'
    lineHeight: '1.0'
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 8px
  container-max: 1200px
  gutter: 24px
  section-padding: 80px
  stack-sm: 12px
  stack-md: 24px
  stack-lg: 48px
---

## Brand & Style

The design system is anchored in a **Corporate Minimalism** aesthetic, optimized for clarity and financial confidence. The target audience includes first-time loan applicants who require a frictionless, non-intimidating experience. The UI evokes a sense of technical precision through its dark theme while maintaining an encouraging tone through high-contrast status indicators and generous negative space.

By stripping away unnecessary ornamentation and focusing on "tonal layering," the design system ensures that the AI’s decision-making process feels transparent and professional. The emotional response is one of security and simplicity, transforming a complex financial evaluation into a guided, modern journey.

## Colors

The palette is strictly functional, utilizing a deep navy foundation to reduce eye strain and provide a premium "fintech" feel. 

- **Primary Background:** The deep navy (#0f172a) acts as the canvas, providing depth.
- **Surface/Cards:** Lighter Slate (#1e293b) distinguishes interactive areas from the background.
- **Semantic Accents:** Approved Green and Rejected Red are used sparingly but vibrantly to provide immediate visual feedback on eligibility status.
- **Typography:** Soft White (#e2e8f0) is the default for readability, while Pure White (#f8fafc) is reserved for high-priority headings.

## Typography

This design system uses **Manrope** for its balance of geometric modernism and functional readability. Given the nature of a loan checker, typography scales are intentionally large to prevent user error during data entry.

- **Headlines:** Use a bold weight and tighter letter spacing for a confident, authoritative presence.
- **Inputs:** Set at a large 20px size to ensure clarity on both mobile and desktop.
- **Labels:** Use uppercase tracking to differentiate field headers from user input.

## Layout & Spacing

The layout follows a **Fixed Grid** model for desktop to maintain a professional, centered focus, transitioning to a fluid single-column for mobile. 

The rhythm is built on an 8px base unit. Ample "breathable" space (80px+) is used between major sections to prevent the user from feeling overwhelmed by financial data. Form fields should be stacked vertically with a 24px (stack-md) gap to guide the user's eye in a singular, downward path.

## Elevation & Depth

Hierarchy is established through **Tonal Layers** rather than heavy shadows. 

1. **Base:** The primary background (#0f172a).
2. **Elevated Surface:** Cards and containers use the Lighter Slate (#1e293b).
3. **Interactive Depth:** On hover, buttons and cards should shift their background color slightly lighter or apply a very subtle, diffused glow using the accent color (0% to 10% opacity) to indicate interactivity. 

Avoid drop shadows that create "muddy" edges; instead, use 1px solid borders (#334155) to define card boundaries clearly.

## Shapes

The design system employs a **Rounded** shape language to soften the serious nature of loan applications. 

- **Cards:** Use `rounded-xl` (1.5rem / 24px) to create a friendly, modern container.
- **Buttons and Inputs:** Use a consistent `rounded-lg` (1rem / 16px) radius. 
- **Icons:** Should feature rounded caps and corners to match the UI's geometry. 

This consistent roundness helps the AI feel approachable and beginner-friendly.

## Components

### Buttons
- **Primary:** Solid color (Soft White text on a subtle slate or blue-tinted background) with a noticeable hover scale-up (1.02x).
- **Success/Action:** Solid Approved Green for the final "Check Eligibility" or "Apply" buttons.

### Input Fields
- **Style:** Large, 1px bordered containers. Focus states should transition the border color to a soft blue or white with a 2px stroke.
- **Validation:** Real-time feedback using the semantic Green/Red for success or error states.

### Cards
- Standard containers for form groups or result summaries. 
- **Result Card:** Large-format card that changes its border color to Green or Red based on the AI’s eligibility output.

### Progress Indicator
- A simple, thin horizontal bar at the top of the viewport to indicate the user's journey through the checking process, minimizing "form fatigue."

### Status Chips
- Small, pill-shaped indicators used within lists or cards to label specific loan types or requirement statuses.