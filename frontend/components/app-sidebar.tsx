"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
    LayoutDashboard,
    Megaphone,
    Users,
    Droplets,
    FileText,
    Settings,
    Fuel,
    Building2,
    Map,
    Activity,
    Package,
    Hash,
    Ruler,
    Archive,
    Truck,
    Award,
    Receipt,
    CreditCard,
    BookOpen,
    Eye,
    UserCog,
    ClipboardList,
    Warehouse
} from "lucide-react";
import { cn } from "@/lib/utils";
import { ThemeToggle } from "@/components/theme-toggle";

const mainNav = [
    { name: "Dashboard", href: "/", icon: LayoutDashboard },
    { name: "Marketing", href: "/marketing", icon: Megaphone },
];

const customerManagementNav = [
    { name: "Customers", href: "/customers", icon: Users },
    { name: "Groups", href: "/customers/groups", icon: Users },
    { name: "Vehicles", href: "/customers/vehicles", icon: Truck },
    { name: "Mappings", href: "/customers/mappings", icon: Map },
];

const employeeManagementNav = [
    { name: "Employees", href: "/employees", icon: UserCog },
    { name: "Company", href: "/company", icon: Building2 },
];

const productManagementNav = [
    { name: "Products", href: "/operations/products", icon: Package },
    { name: "Suppliers", href: "/operations/suppliers", icon: Truck },
    { name: "Lubricant Grades", href: "/operations/grades", icon: Award },
];

const pumpManagementNav = [
    { name: "Station Layout", href: "/operations/station", icon: Map },
    { name: "Tanks", href: "/operations/tanks", icon: Droplets },
    { name: "Pumps", href: "/operations/pumps", icon: Activity },
    { name: "Nozzles", href: "/operations/nozzles", icon: Fuel },
];

const inventoryManagementNav = [
    { name: "Tank Dip Readings", href: "/operations/inventory/tanks", icon: Ruler },
    { name: "Nozzle Meter Readings", href: "/operations/inventory/nozzles", icon: Hash },
    { name: "Product Stock", href: "/operations/inventory/products", icon: Archive },
];

const invoiceManagementNav = [
    { name: "Invoices", href: "/operations/invoices", icon: FileText },
];

const paymentManagementNav = [
    { name: "Credit Overview", href: "/payments/credit", icon: Eye },
    { name: "Statements", href: "/payments/statements", icon: Receipt },
    { name: "Payments", href: "/payments", icon: CreditCard },
    { name: "Customer Ledger", href: "/payments/ledger", icon: BookOpen },
];

const systemNav = [
    { name: "Configurations", href: "/settings", icon: Settings },
];

type NavSection = {
    label: string;
    items: { name: string; href: string; icon: React.ComponentType<{ className?: string }> }[];
};

const sections: NavSection[] = [
    { label: "Main", items: mainNav },
    { label: "Customer Management", items: customerManagementNav },
    { label: "Employee Management", items: employeeManagementNav },
    { label: "Product Management", items: productManagementNav },
    { label: "Pump Management", items: pumpManagementNav },
    { label: "Inventory", items: inventoryManagementNav },
    { label: "Invoice Management", items: invoiceManagementNav },
    { label: "Payment Management", items: paymentManagementNav },
    { label: "System", items: systemNav },
];

export function AppSidebar() {
    const pathname = usePathname();

    const isActive = (href: string) => {
        if (href === "/") return pathname === "/";
        return pathname === href || pathname.startsWith(href + "/");
    };

    return (
        <aside className="w-64 border-r border-border bg-card text-card-foreground flex flex-col h-screen sticky top-0 transition-colors duration-300">
            <div className="h-16 flex items-center px-6 border-b border-border">
                <div className="flex items-center gap-2 text-primary font-bold text-xl">
                    <Fuel className="w-6 h-6" />
                    <span>StopForFuel</span>
                </div>
            </div>

            <nav className="flex-1 overflow-y-auto py-4 px-3 space-y-5">
                {sections.map((section) => (
                    <div key={section.label}>
                        <div className="px-3 mb-1.5 text-[10px] font-bold text-muted-foreground uppercase tracking-widest">
                            {section.label}
                        </div>
                        <div className="space-y-0.5">
                            {section.items.map((item) => (
                                <Link
                                    key={item.name}
                                    href={item.href}
                                    className={cn(
                                        "flex items-center gap-3 px-3 py-2 text-sm font-medium rounded-md transition-colors",
                                        isActive(item.href)
                                            ? "bg-primary/10 text-primary"
                                            : "text-muted-foreground hover:bg-muted hover:text-foreground"
                                    )}
                                >
                                    <item.icon className="w-4 h-4" />
                                    {item.name}
                                </Link>
                            ))}
                        </div>
                    </div>
                ))}
            </nav>

            <div className="p-4 border-t border-border flex items-center justify-between">
                <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-full bg-primary/20 flex items-center justify-center text-primary font-bold">S</div>
                    <div>
                        <p className="text-sm font-medium">Sathya</p>
                        <p className="text-xs text-muted-foreground">Manager</p>
                    </div>
                </div>
                <ThemeToggle />
            </div>
        </aside>
    );
}
