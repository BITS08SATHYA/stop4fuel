"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import {
    getProducts,
    createProduct,
    updateProduct,
    deleteProduct,
    Product,
    getActiveSuppliers,
    getActiveGradeTypes,
    Supplier,
    GradeType
} from "@/lib/api/station";
import { Package, Plus, Edit2, Trash2, Fuel, Box, Truck, Award, Search } from "lucide-react";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";

export default function ProductsPage() {
    const [products, setProducts] = useState<Product[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingProduct, setEditingProduct] = useState<Product | null>(null);
    const [suppliers, setSuppliers] = useState<Supplier[]>([]);
    const [gradeTypes, setGradeTypes] = useState<GradeType[]>([]);
    const [searchQuery, setSearchQuery] = useState("");
    const [categoryFilter, setCategoryFilter] = useState<string>("ALL");
    const [statusFilter, setStatusFilter] = useState<string>("ALL");

    // Form State
    const [name, setName] = useState("");
    const [hsnCode, setHsnCode] = useState("");
    const [price, setPrice] = useState("");
    const [category, setCategory] = useState("Fuel");
    const [unit, setUnit] = useState("Liters");
    const [volume, setVolume] = useState("");
    const [brand, setBrand] = useState("");
    const [supplierId, setSupplierId] = useState("");
    const [gradeTypeId, setGradeTypeId] = useState("");
    const [active, setActive] = useState(true);

    const loadData = async () => {
        setIsLoading(true);
        try {
            const pData = await getProducts();
            setProducts(pData);
            const [sData, gData] = await Promise.all([
                getActiveSuppliers(),
                getActiveGradeTypes()
            ]);
            setSuppliers(sData);
            setGradeTypes(gData);
        } catch (err) {
            console.error("Failed to load data", err);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const openModal = (product?: Product) => {
        if (product) {
            setEditingProduct(product);
            setName(product.name);
            setHsnCode(product.hsnCode);
            setPrice(product.price.toString());
            setCategory(product.category);
            setUnit(product.unit);
            setVolume(product.volume ? product.volume.toString() : "");
            setBrand(product.brand || "");
            setSupplierId(product.supplier?.id.toString() || "");
            setGradeTypeId(product.gradeType?.id.toString() || "");
            setActive(product.active);
        } else {
            setEditingProduct(null);
            setName("");
            setHsnCode("");
            setPrice("");
            setCategory("Fuel");
            setUnit("Liters");
            setVolume("");
            setBrand("");
            setSupplierId("");
            setGradeTypeId("");
            setActive(true);
        }
        setIsModalOpen(true);
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            const payload = {
                name,
                hsnCode,
                price: Number(price),
                category,
                unit,
                volume: volume ? Number(volume) : undefined,
                brand: brand ? brand : undefined,
                supplier: supplierId ? { id: Number(supplierId) } : undefined,
                gradeType: gradeTypeId ? { id: Number(gradeTypeId) } : undefined,
                active
            };

            if (editingProduct) {
                await updateProduct(editingProduct.id, payload as any);
            } else {
                await createProduct(payload as any);
            }
            setIsModalOpen(false);
            loadData();
        } catch (err) {
            console.error("Failed to save product", err);
            alert("Error saving product details");
        }
    };

    const handleDelete = async (id: number) => {
        if (confirm("Are you sure you want to delete this product? This may affect connected tanks or past transactions.")) {
            try {
                await deleteProduct(id);
                loadData();
            } catch (err) {
                console.error("Failed to delete product", err);
                alert("Cannot delete product. It might be in use.");
            }
        }
    };

    // Auto-update unit based on category
    useEffect(() => {
        if (!editingProduct) {
            if (category === "Fuel") {
                setUnit("Liters");
            } else {
                setUnit("Pieces");
            }
        }
    }, [category, editingProduct]);

    const filtered = products.filter((p) => {
        const q = searchQuery.toLowerCase();
        const matchesSearch = !searchQuery || p.name?.toLowerCase().includes(q) || p.hsnCode?.toLowerCase().includes(q) || p.brand?.toLowerCase().includes(q);
        const matchesCategory = categoryFilter === "ALL" || p.category === categoryFilter;
        const matchesStatus = statusFilter === "ALL" || (p.active ? "ACTIVE" : "INACTIVE") === statusFilter;
        return matchesSearch && matchesCategory && matchesStatus;
    });

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData: pagedProducts } = useClientPagination(filtered);

    return (
        <div className="p-8 min-h-screen bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Product <span className="text-gradient">Catalog</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Manage fuel products, lubricants, and other retail items.
                        </p>
                    </div>
                    <button
                        onClick={() => openModal()}
                        className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                    >
                        <Plus className="w-5 h-5" />
                        Add New Product
                    </button>
                </div>

                {/* Filter Bar */}
                <div className="mb-6 flex flex-wrap gap-3 items-center">
                    <div className="relative flex-1 min-w-[200px] max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search by name, HSN, brand..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    <select
                        value={categoryFilter}
                        onChange={(e) => setCategoryFilter(e.target.value)}
                        className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                    >
                        <option value="ALL">All Categories</option>
                        <option value="Fuel">Fuel</option>
                        <option value="Non-Fuel">Non-Fuel</option>
                    </select>
                    <select
                        value={statusFilter}
                        onChange={(e) => setStatusFilter(e.target.value)}
                        className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                    >
                        <option value="ALL">All Status</option>
                        <option value="ACTIVE">Active</option>
                        <option value="INACTIVE">Inactive</option>
                    </select>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4"></div>
                        <p className="animate-pulse">Loading product catalog...</p>
                    </div>
                ) : products.length === 0 ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <Package className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Products Found</h3>
                        <p className="text-muted-foreground mb-6 max-w-md mx-auto">
                            Your product catalog is currently empty. Add fuel products or shop items to get started.
                        </p>
                        <button
                            onClick={() => openModal()}
                            className="bg-primary/10 text-primary hover:bg-primary/20 px-6 py-2 rounded-xl font-medium transition-colors"
                        >
                            Create First Product
                        </button>
                    </div>
                ) : (
                    <GlassCard className="overflow-hidden border-none p-0">
                        <div className="overflow-x-auto">
                            <table className="w-full text-left border-collapse">
                                <thead>
                                    <tr className="bg-white/5 border-b border-border/50">
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-16">#</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Product</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">HSN Code</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Price</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Category</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Details</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-24">Status</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-32">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border/30">
                                    {pagedProducts.map((product, idx) => (
                                        <tr key={product.id} className="hover:bg-white/5 transition-colors group">
                                            <td className="px-6 py-4 text-xs font-mono text-muted-foreground text-center">{page * pageSize + idx + 1}</td>
                                            <td className="px-6 py-4">
                                                <div className="flex items-center gap-3">
                                                    <div className={`p-2.5 rounded-xl ${
                                                        product.category === 'Fuel'
                                                            ? 'bg-orange-500/10 text-orange-500'
                                                            : 'bg-blue-500/10 text-blue-500'
                                                    }`}>
                                                        {product.category === 'Fuel' ? <Fuel className="w-5 h-5" /> : <Box className="w-5 h-5" />}
                                                    </div>
                                                    <div>
                                                        <div className="text-base font-bold text-foreground leading-tight">{product.name}</div>
                                                        {product.brand && (
                                                            <div className="text-xs text-muted-foreground mt-0.5">{product.brand}</div>
                                                        )}
                                                    </div>
                                                </div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <span className="text-sm font-mono text-foreground">{product.hsnCode}</span>
                                            </td>
                                            <td className="px-6 py-4 text-right">
                                                <span className="text-sm font-bold text-primary">₹{(product.price || 0).toFixed(2)}</span>
                                                <span className="text-[10px] text-muted-foreground block">per {product.unit}</span>
                                            </td>
                                            <td className="px-6 py-4 text-center">
                                                <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${
                                                    product.category === 'Fuel'
                                                        ? 'bg-orange-500/10 text-orange-500'
                                                        : 'bg-blue-500/10 text-blue-500'
                                                }`}>
                                                    {product.category}
                                                </span>
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="flex flex-wrap gap-2">
                                                    {product.supplier && (
                                                        <span className="flex items-center gap-1 text-[10px] text-muted-foreground">
                                                            <Truck className="w-3 h-3" /> {product.supplier.name}
                                                        </span>
                                                    )}
                                                    {product.gradeType && (
                                                        <span className="flex items-center gap-1 text-[10px] text-muted-foreground">
                                                            <Award className="w-3 h-3" /> {product.gradeType.name}
                                                        </span>
                                                    )}
                                                    {product.volume && (
                                                        <span className="text-[10px] text-muted-foreground">{product.volume} {product.unit}</span>
                                                    )}
                                                </div>
                                            </td>
                                            <td className="px-6 py-4 text-center">
                                                <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${
                                                    product.active
                                                        ? 'bg-green-500/10 text-green-500 border border-green-500/20'
                                                        : 'bg-red-500/10 text-red-500 border border-red-500/20'
                                                }`}>
                                                    {product.active ? 'Active' : 'Inactive'}
                                                </span>
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="flex justify-center gap-2 opacity-100 md:opacity-0 group-hover:opacity-100 transition-opacity">
                                                    <button
                                                        onClick={() => openModal(product)}
                                                        className="p-2 rounded-lg hover:bg-white/10 text-muted-foreground hover:text-foreground"
                                                    >
                                                        <Edit2 className="w-4 h-4" />
                                                    </button>
                                                    <button
                                                        onClick={() => handleDelete(product.id)}
                                                        className="p-2 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500"
                                                    >
                                                        <Trash2 className="w-4 h-4" />
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                        <TablePagination
                            page={page}
                            totalPages={totalPages}
                            totalElements={totalElements}
                            pageSize={pageSize}
                            onPageChange={setPage}
                        />
                    </GlassCard>
                )}
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title={editingProduct ? "Edit Product" : "Add New Product"}
            >
                <form onSubmit={handleSave} className="space-y-4">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="md:col-span-2">
                            <label className="block text-sm font-medium text-foreground mb-1.5 flex items-center gap-2">
                                Product Name <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="text"
                                required
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all"
                                placeholder="e.g. Premium Petrol"
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5 flex items-center gap-2">
                                HSN Code <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="text"
                                required
                                value={hsnCode}
                                onChange={(e) => setHsnCode(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all"
                                placeholder="e.g. 2710"
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5 flex items-center gap-2">
                                Price (₹) <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="number"
                                step="0.01"
                                required
                                min="0"
                                value={price}
                                onChange={(e) => setPrice(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all font-mono"
                                placeholder="0.00"
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5 flex items-center gap-2">
                                Category <span className="text-red-500">*</span>
                            </label>
                            <select
                                required
                                value={category}
                                onChange={(e) => setCategory(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all appearance-none"
                            >
                                <option value="Fuel">Fuel</option>
                                <option value="Non-Fuel">Non-Fuel (Lubes, Items)</option>
                            </select>
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5 flex items-center gap-2">
                                Unit <span className="text-red-500">*</span>
                            </label>
                            <select
                                required
                                value={unit}
                                onChange={(e) => setUnit(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all appearance-none"
                            >
                                <option value="Liters">Liters</option>
                                <option value="Pieces">Pieces</option>
                                <option value="Kg">Kg</option>
                                <option value="Box">Box</option>
                            </select>
                        </div>

                        {category === 'Non-Fuel' && (
                            <>
                                <div>
                                    <label className="block text-sm font-medium text-foreground mb-1.5">
                                        Volume/Weight <span className="text-muted-foreground font-normal text-xs">(Optional)</span>
                                    </label>
                                    <input
                                        type="number"
                                        step="0.01"
                                        min="0"
                                        value={volume}
                                        onChange={(e) => setVolume(e.target.value)}
                                        className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all"
                                        placeholder="e.g. 1.0"
                                    />
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-foreground mb-1.5 flex items-center gap-2">
                                        <Truck className="w-4 h-4 text-primary" /> Supplier <span className="text-muted-foreground font-normal text-xs">(Optional)</span>
                                    </label>
                                    <select
                                        value={supplierId}
                                        onChange={(e) => setSupplierId(e.target.value)}
                                        className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all appearance-none"
                                    >
                                        <option value="">Select Supplier...</option>
                                        {suppliers.map(s => (
                                            <option key={s.id} value={s.id}>{s.name}</option>
                                        ))}
                                    </select>
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-foreground mb-1.5 flex items-center gap-2">
                                        <Award className="w-4 h-4 text-primary" /> Lubricant Grade <span className="text-muted-foreground font-normal text-xs">(Optional)</span>
                                    </label>
                                    <select
                                        value={gradeTypeId}
                                        onChange={(e) => setGradeTypeId(e.target.value)}
                                        className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all appearance-none"
                                    >
                                        <option value="">Select Grade...</option>
                                        {gradeTypes.map(g => (
                                            <option key={g.id} value={g.id}>{g.name}</option>
                                        ))}
                                    </select>
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-foreground mb-1.5">
                                        Brand Name <span className="text-muted-foreground font-normal text-xs">(Optional)</span>
                                    </label>
                                    <input
                                        type="text"
                                        value={brand}
                                        onChange={(e) => setBrand(e.target.value)}
                                        className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all"
                                        placeholder="e.g. Castrol"
                                    />
                                </div>
                            </>
                        )}
                    </div>

                    <div className="flex items-center justify-between p-4 bg-black/5 dark:bg-white/5 rounded-xl border border-border mt-2">
                        <div>
                            <p className="font-medium text-foreground text-sm">Product Status</p>
                            <p className="text-xs text-muted-foreground">Is this product currently available for sale?</p>
                        </div>
                        <label className="relative inline-flex items-center cursor-pointer">
                            <input
                                type="checkbox"
                                className="sr-only peer"
                                checked={active}
                                onChange={(e) => setActive(e.target.checked)}
                            />
                            <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-primary/20 dark:peer-focus:ring-primary/30 rounded-full peer dark:bg-gray-700 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all dark:border-gray-600 peer-checked:bg-primary"></div>
                        </label>
                    </div>

                    <div className="flex justify-end gap-3 pt-6 border-t border-border mt-6">
                        <button
                            type="button"
                            onClick={() => setIsModalOpen(false)}
                            className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 dark:hover:bg-white/10 transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            className="btn-gradient px-8 py-2.5 rounded-xl font-medium shadow-lg hover:shadow-xl transition-all"
                        >
                            {editingProduct ? "Save Changes" : "Create Product"}
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
