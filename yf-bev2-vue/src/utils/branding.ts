import lightLogo from '@/assets/imgs/tripeer-logo-light.png'

export const BRAND_LOGO = '/tripeer-logo-transparent.png'
export const BRAND_LOGO_LIGHT = lightLogo
export const BRAND_MARK = '/tripeer-mark.svg'

// Map only bundled, same-origin defaults; leave uploaded/external brands intact.
export const isBuiltinBrandLogo = (source?: string): boolean => {
  if (!source) return true
  try {
    const url = new URL(source, window.location.origin)
    return (
      url.origin === window.location.origin &&
      ['/brand-logo.png', '/logo.png', BRAND_LOGO, BRAND_MARK].includes(url.pathname)
    )
  } catch {
    return false
  }
}
