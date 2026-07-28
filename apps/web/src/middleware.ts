import { NextResponse, type NextRequest } from "next/server";

const API_ORIGIN = process.env.API_ORIGIN || "http://127.0.0.1:8080";

/** Routes reachable without a session. */
const PUBLIC_PATHS = ["/login", "/setup", "/invite/accept"];

function isPublic(pathname: string): boolean {
  return PUBLIC_PATHS.some(
    (p) => pathname === p || pathname.startsWith(p + "/"),
  );
}

function hasSession(req: NextRequest): boolean {
  return (
    req.cookies.has("cairn_access") || req.cookies.has("cairn_refresh")
  );
}

async function needsBootstrap(): Promise<boolean> {
  try {
    const res = await fetch(`${API_ORIGIN}/api/v1/auth/bootstrap-status`, {
      headers: { accept: "application/json" },
      cache: "no-store",
    });
    if (!res.ok) return false;
    const body = (await res.json()) as {
      data?: { needsBootstrap?: boolean };
    };
    return body?.data?.needsBootstrap === true;
  } catch {
    // If the API is unreachable, don't trap the user in a redirect loop.
    return false;
  }
}

export async function middleware(req: NextRequest) {
  const { pathname } = req.nextUrl;
  const authed = hasSession(req);

  // Authenticated users should not sit on login/setup.
  if (authed && (pathname === "/login" || pathname === "/setup")) {
    return NextResponse.redirect(new URL("/my-tasks", req.url));
  }

  if (isPublic(pathname)) {
    // Keep bootstrap vs login coherent for unauthenticated visitors.
    if (!authed && (pathname === "/login" || pathname === "/setup")) {
      const bootstrap = await needsBootstrap();
      if (bootstrap && pathname === "/login") {
        return NextResponse.redirect(new URL("/setup", req.url));
      }
      if (!bootstrap && pathname === "/setup") {
        return NextResponse.redirect(new URL("/login", req.url));
      }
    }
    return NextResponse.next();
  }

  // Protected route without a session → setup (first run) or login.
  if (!authed) {
    const bootstrap = await needsBootstrap();
    const dest = bootstrap ? "/setup" : "/login";
    return NextResponse.redirect(new URL(dest, req.url));
  }

  return NextResponse.next();
}

export const config = {
  // Skip Next internals, the API rewrite, and static files.
  matcher: ["/((?!_next/static|_next/image|api|favicon.ico|.*\\..*).*)"],
};
