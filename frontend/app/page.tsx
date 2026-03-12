import { GlassCard } from "@/components/ui/glass-card";
import { OverviewChart } from "@/components/overview-chart";
import { RecentSales } from "@/components/recent-sales";
import { Fuel, DollarSign, Activity, Droplets, Power } from "lucide-react";

export default function DashboardPage() {
  return (
    <div className="min-h-screen bg-background p-8 transition-colors duration-300">
      {/* Header */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-3xl font-bold text-foreground tracking-tight">Dashboard</h1>
          <p className="text-muted-foreground mt-1">
            Shift ID: <span className="font-mono text-cyan-400">SCID-20241203-A</span>
          </p>
        </div>
        <button className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg font-medium flex items-center gap-2 transition-colors">
          <Power className="w-4 h-4" />
          Close Shift
        </button>
      </div>

      {/* Stats Grid */}
      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-4 mb-8">
        <GlassCard className="relative overflow-hidden">
          <div className="flex justify-between items-start">
            <div>
              <p className="text-sm font-medium text-muted-foreground">Total Revenue</p>
              <h3 className="text-2xl font-bold text-foreground mt-2">$45,231.89</h3>
              <p className="text-xs text-emerald-400 mt-1">+20.1% from last month</p>
            </div>
            <div className="p-2 bg-emerald-500/10 rounded-lg">
              <DollarSign className="w-5 h-5 text-emerald-400" />
            </div>
          </div>
        </GlassCard>

        <GlassCard className="relative overflow-hidden">
          <div className="flex justify-between items-start">
            <div>
              <p className="text-sm font-medium text-muted-foreground">Fuel Volume Sold</p>
              <h3 className="text-2xl font-bold text-foreground mt-2">12,345 L</h3>
              <p className="text-xs text-emerald-400 mt-1">+15% from yesterday</p>
            </div>
            <div className="p-2 bg-cyan-500/10 rounded-lg">
              <Fuel className="w-5 h-5 text-cyan-400" />
            </div>
          </div>
        </GlassCard>

        <GlassCard className="relative overflow-hidden">
          <div className="flex justify-between items-start">
            <div>
              <p className="text-sm font-medium text-muted-foreground">Active Nozzles</p>
              <h3 className="text-2xl font-bold text-foreground mt-2">12/16</h3>
              <p className="text-xs text-rose-400 mt-1">4 Maintenance</p>
            </div>
            <div className="p-2 bg-rose-500/10 rounded-lg">
              <Activity className="w-5 h-5 text-rose-400" />
            </div>
          </div>
        </GlassCard>

        <GlassCard className="relative overflow-hidden">
          <div className="flex justify-between items-start">
            <div>
              <p className="text-sm font-medium text-muted-foreground">Tank Inventory</p>
              <h3 className="text-2xl font-bold text-foreground mt-2">85%</h3>
              <p className="text-xs text-amber-400 mt-1">Refill scheduled</p>
            </div>
            <div className="p-2 bg-amber-500/10 rounded-lg">
              <Droplets className="w-5 h-5 text-amber-400" />
            </div>
          </div>
        </GlassCard>
      </div>

      {/* Main Content Grid */}
      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-7">
        {/* Chart Section */}
        <GlassCard className="col-span-4">
          <div className="mb-6">
            <h3 className="text-lg font-semibold text-foreground">Hourly Sales</h3>
            <p className="text-sm text-muted-foreground">Fuel sales distribution for current shift.</p>
          </div>
          <div className="h-[300px] w-full">
            <OverviewChart />
          </div>
        </GlassCard>

        {/* Recent Transactions */}
        <GlassCard className="col-span-3">
          <div className="mb-6">
            <h3 className="text-lg font-semibold text-foreground">Recent Transactions</h3>
            <p className="text-sm text-muted-foreground">Latest invoices generated.</p>
          </div>
          <RecentSales />
        </GlassCard>
      </div>
    </div>
  );
}
