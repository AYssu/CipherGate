/** Public service pages and portal tokens must never trigger the administrator session client. */
export function isAdminPath(pathname: string): boolean {
  return /^\/(?:dashboard|profile|applications|user|system|membership|operation|plugins|admin\/docs)(?:\/|$)/.test(pathname);
}
