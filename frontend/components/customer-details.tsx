import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { Mail, Phone, MapPin, Truck, Fuel, X } from "lucide-react";

export function CustomerDetails() {
    return (
        <div className="glass-panel h-full rounded-2xl p-6 border border-white/10 flex flex-col relative">
            <button className="absolute top-4 right-4 text-muted-foreground hover:text-white">
                <X className="w-5 h-5" />
            </button>

            {/* Header */}
            <div className="flex flex-col items-center mb-8">
                <div className="w-20 h-20 rounded-full bg-gradient-to-br from-cyan-500 to-blue-600 p-0.5 mb-4">
                    <div className="w-full h-full rounded-full bg-black/50 backdrop-blur-sm flex items-center justify-center border-2 border-transparent">
                        <span className="text-2xl font-bold text-white">JD</span>
                    </div>
                </div>
                <h2 className="text-xl font-bold text-white">John Doe</h2>
                <p className="text-cyan-400 text-sm">Individual Customer</p>
            </div>

            {/* Contact Info */}
            <div className="space-y-4 mb-8">
                <div className="flex items-center text-sm text-muted-foreground">
                    <Mail className="w-4 h-4 mr-3 text-white/50" />
                    john.doe@email.com
                </div>
                <div className="flex items-center text-sm text-muted-foreground">
                    <Phone className="w-4 h-4 mr-3 text-white/50" />
                    +1 555-0123
                </div>
                <div className="flex items-center text-sm text-muted-foreground">
                    <MapPin className="w-4 h-4 mr-3 text-white/50" />
                    123 Main St, New York, NY
                </div>
            </div>

            {/* Linked Vehicles */}
            <div>
                <h3 className="text-sm font-semibold text-white mb-4 uppercase tracking-wider">Linked Vehicles</h3>
                <div className="grid grid-cols-2 gap-3">
                    {[1, 2, 3].map((i) => (
                        <div key={i} className="bg-white/5 border border-white/10 rounded-lg p-3 hover:bg-white/10 transition-colors cursor-pointer">
                            <div className="flex justify-between items-start mb-2">
                                <Truck className="w-5 h-5 text-cyan-400" />
                                <Badge variant="outline" className="text-[10px] px-1.5">Active</Badge>
                            </div>
                            <p className="text-white font-medium text-sm">TRK-10{i}</p>
                            <p className="text-xs text-muted-foreground mt-1">Volvo FH16</p>
                        </div>
                    ))}
                </div>
            </div>

            {/* Fuel Stats */}
            <div className="mt-8">
                <h3 className="text-sm font-semibold text-white mb-4 uppercase tracking-wider">Fuel Consumption</h3>
                <div className="bg-white/5 rounded-xl p-4 border border-white/10">
                    <div className="flex items-center justify-between mb-2">
                        <span className="text-xs text-muted-foreground">Last 30 Days</span>
                        <span className="text-emerald-400 text-xs font-medium">+5.2%</span>
                    </div>
                    <div className="text-2xl font-bold text-white mb-1">3,500 <span className="text-sm font-normal text-muted-foreground">Gallons</span></div>

                    {/* Fake Chart Line */}
                    <div className="h-10 flex items-end gap-1 mt-2">
                        {[40, 60, 45, 70, 50, 80, 65, 90].map((h, i) => (
                            <div key={i} className="flex-1 bg-cyan-500/20 rounded-t-sm hover:bg-cyan-500/50 transition-colors" style={{ height: `${h}%` }} />
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
}
