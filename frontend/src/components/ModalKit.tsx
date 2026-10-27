"use client";

import { X } from "lucide-react";

export function ModalShell({
    children,
    borderClass = "border-slate-100",
    widthClass = "max-w-md",
    animated = false,
    closing = false,
}: {
    children: React.ReactNode;
    borderClass?: string;
    widthClass?: string;
    // 열고 닫을 때 배경 페이드와 카드 팝 모션을 쓴다 (기본은 모션 없음)
    animated?: boolean;
    closing?: boolean;
}) {
    const backdropMotion = animated ? (closing ? "anim-backdrop-out" : "anim-backdrop-in") : "";
    const cardMotion = animated ? (closing ? "anim-modal-pop-out" : "anim-modal-pop-in") : "";

    return (
        <div
            className={`fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4 ${backdropMotion}`}
        >
            <div
                className={`bg-white w-full ${widthClass} rounded-3xl shadow-2xl border ${borderClass} p-6 max-h-[90vh] overflow-y-auto ${cardMotion}`}
            >
                {children}
            </div>
        </div>
    );
}

export function ModalHeader({
    title,
    subtitle,
    icon,
    onClose,
    titleClass = "text-slate-900",
}: {
    title: string;
    subtitle?: string;
    icon?: React.ReactNode;
    onClose: () => void;
    titleClass?: string;
}) {
    return (
        <div className="flex items-center justify-between pb-3 border-b border-slate-100">
            <div>
                <h2 className={`text-lg font-bold flex items-center gap-1.5 ${titleClass}`}>
                    {icon}
                    <span>{title}</span>
                </h2>
                {subtitle && <p className="text-xs text-slate-500 mt-0.5">{subtitle}</p>}
            </div>
            <button onClick={onClose} className="p-1 rounded-full hover:bg-slate-100 text-slate-400">
                <X className="w-5 h-5" />
            </button>
        </div>
    );
}

export function ImageLightbox({
    src,
    alt,
    onClose,
}: {
    src: string;
    alt: string;
    onClose: () => void;
}) {
    return (
        <div
            className="fixed inset-0 z-[60] flex items-center justify-center bg-black/85 p-4 cursor-zoom-out"
            onClick={onClose}
        >
            <button
                onClick={onClose}
                className="absolute top-4 right-4 p-2 rounded-full bg-white/10 hover:bg-white/20 text-white cursor-pointer"
            >
                <X className="w-6 h-6" />
            </button>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
                src={src}
                alt={alt}
                className="max-w-full max-h-full object-contain rounded-lg cursor-default"
                onClick={(e) => e.stopPropagation()}
            />
        </div>
    );
}

export function ModalActions({
    onClose,
    submitting,
    submitLabel,
    tone = "emerald",
}: {
    onClose: () => void;
    submitting: boolean;
    submitLabel: string;
    tone?: "emerald" | "rose";
}) {
    const toneClass = tone === "rose" ? "bg-rose-500 hover:bg-rose-600" : "bg-emerald-500 hover:bg-emerald-600";
    return (
        <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
            <button
                type="button"
                onClick={onClose}
                disabled={submitting}
                className="px-4 py-2 rounded-xl text-sm font-bold text-slate-600 hover:bg-slate-100 disabled:opacity-50"
            >
                취소
            </button>
            <button
                type="submit"
                disabled={submitting}
                className={`px-5 py-2 rounded-xl text-sm font-bold text-white shadow-sm disabled:opacity-50 ${toneClass}`}
            >
                {submitting ? "처리 중..." : submitLabel}
            </button>
        </div>
    );
}
