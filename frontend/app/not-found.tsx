import { Home, Search } from "lucide-react";
import Link from "next/link";

export default function NotFound() {
    return (
        <div className="flex flex-1 items-center justify-center p-8">
            <div className="mx-auto max-w-md text-center">
                <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-full bg-yellow-500/10">
                    <Search className="h-8 w-8 text-yellow-500" />
                </div>
                <h1 className="mb-2 text-2xl font-bold">Page Not Found</h1>
                <p className="mb-6 text-muted-foreground">
                    The page you&apos;re looking for doesn&apos;t exist or has
                    been moved.
                </p>
                <Link
                    href="/dashboard"
                    className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
                >
                    <Home className="h-4 w-4" />
                    Back to Dashboard
                </Link>
            </div>
        </div>
    );
}
