// Presentation only; permissions are enforced by the existing Java API.
export function presentationRole(role) {
 const value = String(role ?? '').trim().toLowerCase().replace(/[\s-]+/g,'_');
 return ({admin:'admin',front_desk:'front_desk',customer:'customer',housekeeping:'housekeeping',maintenance:'maintenance','f&b':'f&b'})[value] ?? null;
}
export function isDepartmentalRole(role) { return ['housekeeping','maintenance','f&b'].includes(presentationRole(role)); }
