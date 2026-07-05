import { Badge } from "@/components/ui/badge";

/**
 * Vehicle label for a statement row: vehicle-wise splits carry exactly one
 * vehicle number (shown as a badge); customer-wide statements spanning several
 * vehicles show a muted count; statements with no vehicle bills show nothing.
 */
export function StatementVehicleTag({ vehicleNumbers }: { vehicleNumbers?: string[] }) {
    if (!vehicleNumbers || vehicleNumbers.length === 0) return null;
    if (vehicleNumbers.length === 1) {
        return (
            <Badge variant="outline" className="text-[9px] font-mono whitespace-nowrap">
                {vehicleNumbers[0]}
            </Badge>
        );
    }
    return (
        <span className="text-[10px] text-muted-foreground whitespace-nowrap">
            {vehicleNumbers.length} vehicles
        </span>
    );
}
