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
    Warehouse,
    ShoppingBag,
    ArrowLeftRight,
    Clock,
    BarChart3,
    PieChart,
    Wallet,
    Banknote,
    Gift,
    History,
    CalendarDays,
    CalendarCheck,
    IndianRupee,
    Zap,
    TrendingUp,
    ShoppingCart,
    Brain,
    Shield,
    UserCheck,
    Tag,
    Layers,
    LogOut,
    Bell,
    ClipboardList,
    MapPin,
    Search,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { ThemeToggle } from "@/components/theme-toggle";
import { useAuth } from "@/lib/auth/auth-context";

const mainNav = [
    { name: "Dashboard", href: "/dashboard", icon: LayoutDashboard },
    { name: "Marketing", href: "/marketing", icon: Megaphone },
];

const customerManagementNav = [
    { name: "Customers", href: "/customers", icon: Users },
    { name: "Customer Map", href: "/customers/map", icon: MapPin },
    { name: "Groups", href: "/customers/groups", icon: Users },
    { name: "Categories", href: "/customers/categories", icon: Layers },
    { name: "Vehicles", href: "/customers/vehicles", icon: Truck },
    { name: "Mappings", href: "/customers/mappings", icon: Map },
    { name: "Incentives", href: "/customers/incentives", icon: Tag },
];

const employeeManagementNav = [
    { name: "Employees", href: "/employees", icon: UserCog },
    { name: "Attendance", href: "/employees/attendance", icon: CalendarCheck },
    { name: "Leave Management", href: "/employees/leaves", icon: CalendarDays },
    { name: "Salary Processing", href: "/employees/salary", icon: IndianRupee },
    { name: "Company", href: "/company", icon: Building2 },
];

const productManagementNav = [
    { name: "Products", href: "/operations/products", icon: Package },
    { name: "Suppliers", href: "/operations/suppliers", icon: Truck },
    { name: "Oil Types", href: "/operations/oil-types", icon: Droplets },
    { name: "Lubricant Grades", href: "/operations/grades", icon: Award },
];

const pumpManagementNav = [
    { name: "Station Layout", href: "/operations/station", icon: Map },
    { name: "Tanks", href: "/operations/tanks", icon: Droplets },
    { name: "Pumps", href: "/operations/pumps", icon: Activity },
    { name: "Nozzles", href: "/operations/nozzles", icon: Fuel },
];

const inventoryManagementNav = [
    { name: "Operational Dashboard", href: "/operations/dashboard", icon: BarChart3 },
    { name: "Tank Dip Readings", href: "/operations/inventory/tanks", icon: Ruler },
    { name: "Nozzle Meter Readings", href: "/operations/inventory/nozzles", icon: Hash },
    { name: "Product Stock", href: "/operations/inventory/products", icon: Archive },
    { name: "Godown Stock", href: "/operations/inventory/godown", icon: Warehouse },
    { name: "Cashier Stock", href: "/operations/inventory/cashier", icon: ShoppingBag },
    { name: "Stock Transfer", href: "/operations/inventory/transfers", icon: ArrowLeftRight },
    { name: "Purchase Invoices", href: "/operations/inventory/purchase-invoices", icon: FileText },
];

const shiftManagementNav = [
    { name: "Shift Register", href: "/operations/shifts", icon: Clock },
    { name: "Shift History", href: "/operations/shifts/history", icon: History },
    { name: "Operational Advances", href: "/operations/advances", icon: Wallet },
    { name: "E-Advances", href: "/operations/e-advances", icon: CreditCard },
    { name: "Incentive Payments", href: "/operations/incentive-payments", icon: Gift },
    { name: "Cash Inflows", href: "/operations/cash-inflows", icon: Banknote },
];

const invoiceManagementNav = [
    { name: "Invoice Dashboard", href: "/operations/invoices/dashboard", icon: BarChart3 },
    { name: "Invoices", href: "/operations/invoices", icon: FileText },
    { name: "Invoice History", href: "/operations/invoices/history", icon: History },
    { name: "Invoice Explorer", href: "/operations/invoices/explorer", icon: Search },
];

const paymentManagementNav = [
    { name: "Payment Dashboard", href: "/payments/dashboard", icon: PieChart },
    { name: "Credit Overview", href: "/payments/credit", icon: Eye },
    { name: "Statements", href: "/payments/statements", icon: Receipt },
    { name: "Explorer", href: "/payments/explorer", icon: Search },
    { name: "Payments", href: "/payments", icon: CreditCard },
    { name: "Customer Ledger", href: "/payments/ledger", icon: BookOpen },
];

const financeNav = [
    { name: "Utility Bills", href: "/operations/utility-bills", icon: Zap },
    { name: "Expenses", href: "/operations/expenses", icon: Receipt },
];

const reportsNav = [
    { name: "Reports", href: "/operations/reports", icon: ClipboardList },
];

const analyticsNav = [
    { name: "Sales Forecast", href: "/analytics/sales", icon: TrendingUp },
    { name: "Purchase Planner", href: "/analytics/purchases", icon: ShoppingCart },
    { name: "Profitability", href: "/analytics/profitability", icon: Brain },
    { name: "Credit Intelligence", href: "/analytics/credit", icon: Shield },
    { name: "Employee Insights", href: "/analytics/employees", icon: UserCheck },
];

const systemNav = [
    { name: "Users", href: "/settings/users", icon: UserCog },
    { name: "Configurations", href: "/settings", icon: Settings },
    { name: "Notifications", href: "/settings/notifications", icon: Bell },
];

// Cashier-specific restricted navigation
const cashierSections: NavSection[] = [
    {
        label: "Main",
        permission: "DASHBOARD_VIEW",
        items: [
            { name: "Dashboard", href: "/dashboard", icon: LayoutDashboard },
        ],
    },
    {
        label: "Shift Operations",
        permission: "SHIFT_VIEW",
        items: [
            { name: "Invoice Bills", href: "/operations/invoices", icon: FileText },
            { name: "View Invoices", href: "/operations/invoices/history", icon: Eye },
            { name: "Payments", href: "/payments", icon: CreditCard },
            { name: "Expenses", href: "/operations/expenses", icon: Receipt },
            { name: "Incentive Payments", href: "/operations/incentive-payments", icon: Gift },
            { name: "Operational Advances", href: "/operations/advances", icon: Wallet },
            { name: "E-Advances", href: "/operations/e-advances", icon: CreditCard },
            { name: "Shift Closing", href: "/operations/shifts", icon: Clock },
        ],
    },
];

// Customer-specific portal navigation
const customerSections: NavSection[] = [
    {
        label: "My Account",
        permission: "DASHBOARD_VIEW",
        items: [
            { name: "Dashboard", href: "/dashboard", icon: LayoutDashboard },
        ],
    },
    {
        label: "Financial",
        permission: "DASHBOARD_VIEW",
        items: [
            { name: "Statements", href: "/customer/statements", icon: Receipt },
            { name: "Payments", href: "/customer/payments", icon: CreditCard },
            { name: "Invoices", href: "/customer/invoices", icon: FileText },
        ],
    },
    {
        label: "Vehicles",
        permission: "DASHBOARD_VIEW",
        items: [
            { name: "My Vehicles", href: "/customer/vehicles", icon: Truck },
        ],
    },
];

// Employee-specific restricted navigation
const employeeSections: NavSection[] = [
    {
        label: "Main",
        permission: "DASHBOARD_VIEW",
        items: [
            { name: "Dashboard", href: "/dashboard", icon: LayoutDashboard },
        ],
    },
    {
        label: "My Info",
        permission: "DASHBOARD_VIEW",
        items: [
            { name: "My Attendance", href: "/employees/attendance", icon: CalendarCheck },
            { name: "My Leaves", href: "/employees/leaves", icon: CalendarDays },
            { name: "My Salary", href: "/employees/salary", icon: IndianRupee },
        ],
    },
];

type NavSection = {
    label: string;
    permission: string;
    items: { name: string; href: string; icon: React.ComponentType<{ className?: string }> }[];
};

const sections: NavSection[] = [
    { label: "Main", permission: "DASHBOARD_VIEW", items: mainNav },
    { label: "Customer Management", permission: "CUSTOMER_VIEW", items: customerManagementNav },
    { label: "Employee Management", permission: "EMPLOYEE_VIEW", items: employeeManagementNav },
    { label: "Product Management", permission: "PRODUCT_VIEW", items: productManagementNav },
    { label: "Pump Management", permission: "STATION_VIEW", items: pumpManagementNav },
    { label: "Inventory", permission: "INVENTORY_VIEW", items: inventoryManagementNav },
    { label: "Shift Management", permission: "SHIFT_VIEW", items: shiftManagementNav },
    { label: "Invoice Management", permission: "INVOICE_VIEW", items: invoiceManagementNav },
    { label: "Payment Management", permission: "PAYMENT_VIEW", items: paymentManagementNav },
    { label: "Finance", permission: "FINANCE_VIEW", items: financeNav },
    { label: "Reports", permission: "REPORT_VIEW", items: reportsNav },
    { label: "Analytics", permission: "DASHBOARD_VIEW", items: analyticsNav },
    { label: "System", permission: "SETTINGS_VIEW", items: systemNav },
];

export function AppSidebar() {
    const pathname = usePathname();
    const { user, hasPermission, logout } = useAuth();

    const isActive = (href: string) => {
        if (href === "/dashboard") return pathname === "/dashboard";
        return pathname === href || pathname.startsWith(href + "/");
    };

    const isCustomer = user?.role === "CUSTOMER";
    const isCashier = user?.designation === "Cashier" && user?.role !== "OWNER" && user?.role !== "ADMIN";
    const isEmployee = user?.role === "EMPLOYEE" && !isCashier;
    const activeSections = isCustomer ? customerSections : isCashier ? cashierSections : isEmployee ? employeeSections : sections;
    const filteredSections = activeSections.filter(section => hasPermission(section.permission));

    return (
        <aside className="w-64 border-r border-border bg-card text-card-foreground flex flex-col h-screen sticky top-0 transition-colors duration-300">
            <div className="h-16 flex items-center px-6 border-b border-border">
                <div className="flex items-center gap-2 text-primary font-bold text-xl">
                    <Fuel className="w-6 h-6" />
                    <span>StopForFuel</span>
                </div>
            </div>

            <nav className="flex-1 overflow-y-auto py-4 px-3 space-y-5">
                {filteredSections.map((section) => (
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
                    <div className="w-8 h-8 rounded-full bg-primary/20 flex items-center justify-center text-primary font-bold">
                        {user?.name?.charAt(0)?.toUpperCase() || "U"}
                    </div>
                    <div>
                        <p className="text-sm font-medium">{user?.name || "User"}</p>
                        <p className="text-xs text-muted-foreground">{user?.role || "..."}</p>
                    </div>
                </div>
                <div className="flex items-center gap-1">
                    <ThemeToggle />
                    <button
                        onClick={logout}
                        className="p-2 rounded-md text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
                        title="Sign out"
                    >
                        <LogOut className="w-4 h-4" />
                    </button>
                </div>
            </div>
        </aside>
    );
}
