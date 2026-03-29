import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const PUBLIC_PATHS = ["/login", "/auth/callback", "/forgot-password", "/marketing"];
const ROOT_PATH = "/";

function isPublicPath(pathname: string): boolean {
    if (pathname === ROOT_PATH) return true;
    return PUBLIC_PATHS.some((p) => pathname.startsWith(p));
}

export function middleware(request: NextRequest) {
    const { pathname } = request.nextUrl;

    // Skip static assets and API routes
    if (
        pathname.startsWith("/_next") ||
        pathname.startsWith("/api") ||
        pathname.includes(".")
    ) {
        return NextResponse.next();
    }

    // Public paths are always accessible
    if (isPublicPath(pathname)) {
        return NextResponse.next();
    }

    // In dev mode (no Cognito configured), allow all routes
    if (!process.env.NEXT_PUBLIC_COGNITO_USER_POOL_ID) {
        return NextResponse.next();
    }

    // Check for auth session cookie (set by client on successful login)
    const authCookie = request.cookies.get("sff-auth-session");
    if (!authCookie?.value) {
        const loginUrl = new URL("/login", request.url);
        loginUrl.searchParams.set("returnTo", pathname);
        return NextResponse.redirect(loginUrl);
    }

    return NextResponse.next();
}

export const config = {
    matcher: [
        /*
         * Match all request paths except:
         * - _next/static (static files)
         * - _next/image (image optimization)
         * - favicon.ico, sitemap.xml, robots.txt
         */
        "/((?!_next/static|_next/image|favicon.ico|sitemap.xml|robots.txt).*)",
    ],
};
