// Renders the XC TV Wall app icon (Fire TV tile) and the Android TV banner as PNGs.
// Usage (macOS, Montserrat installed): swift tools/render-icons.swift app/src/main/res
import AppKit

let res = URL(fileURLWithPath: CommandLine.arguments[1])

func hex(_ v: UInt32, _ a: CGFloat = 1) -> NSColor {
    NSColor(srgbRed: CGFloat((v >> 16) & 0xFF) / 255, green: CGFloat((v >> 8) & 0xFF) / 255,
            blue: CGFloat(v & 0xFF) / 255, alpha: a)
}
let cyan = hex(0x00F5FF), green = hex(0x00FF4C)
let navyTop = hex(0x14203A), navyBottom = hex(0x060A13), ink = hex(0x0B1220)

func render(_ w: Int, _ h: Int, to path: String, _ draw: (CGFloat, CGFloat) -> Void) {
    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: w, pixelsHigh: h, bitsPerSample: 8,
                               samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                               colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    let ctx = NSGraphicsContext(bitmapImageRep: rep)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(cgContext: ctx.cgContext, flipped: true)
    ctx.cgContext.translateBy(x: 0, y: CGFloat(h)); ctx.cgContext.scaleBy(x: 1, y: -1) // y grows downward
    ctx.imageInterpolation = .high
    draw(CGFloat(w), CGFloat(h))
    NSGraphicsContext.restoreGraphicsState()
    let url = res.appendingPathComponent(path)
    try! FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    try! rep.representation(using: .png, properties: [:])!.write(to: url)
}

func navyBackground(_ r: NSRect, radius: CGFloat) {
    let p = NSBezierPath(roundedRect: r, xRadius: radius, yRadius: radius)
    NSGradient(starting: navyTop, ending: navyBottom)!.draw(in: p, angle: 90) // flipped: top → bottom
    // Soft brand glow from the lower-right corner.
    NSGraphicsContext.saveGraphicsState(); p.addClip()
    NSGradient(colors: [hex(0x00FF8C, 0.20), hex(0x00FF8C, 0)])!
        .draw(fromCenter: NSPoint(x: r.maxX, y: r.maxY), radius: 0,
              toCenter: NSPoint(x: r.maxX, y: r.maxY), radius: r.width * 0.9, options: [])
    NSGraphicsContext.restoreGraphicsState()
}

/// The mark: a glowing screen with a slide stacked behind it, a play symbol, and a stand.
/// Drawn inside the square [box]; its own bounds are roughly the middle 72 % of the box.
func mark(_ box: NSRect) {
    func r(_ x0: CGFloat, _ y0: CGFloat, _ x1: CGFloat, _ y1: CGFloat) -> NSRect {
        NSRect(x: box.minX + x0 * box.width, y: box.minY + y0 * box.height,
               width: (x1 - x0) * box.width, height: (y1 - y0) * box.height)
    }
    let s = box.width
    // Slide behind the screen.
    let back = NSBezierPath(roundedRect: r(0.22, 0.20, 0.86, 0.60), xRadius: s * 0.06, yRadius: s * 0.06)
    hex(0xFFFFFF, 0.16).setFill(); back.fill()
    hex(0xFFFFFF, 0.30).setStroke(); back.lineWidth = s * 0.012; back.stroke()
    // Screen.
    let screenRect = r(0.14, 0.27, 0.78, 0.69)
    let screen = NSBezierPath(roundedRect: screenRect, xRadius: s * 0.065, yRadius: s * 0.065)
    NSGraphicsContext.saveGraphicsState()
    let shadow = NSShadow(); shadow.shadowColor = hex(0x00FFB0, 0.45)
    shadow.shadowBlurRadius = s * 0.06; shadow.shadowOffset = .zero; shadow.set()
    cyan.setFill(); screen.fill()
    NSGraphicsContext.restoreGraphicsState()
    NSGradient(starting: cyan, ending: green)!.draw(in: screen, angle: -35)
    // Play symbol with rounded corners.
    let cx = screenRect.midX + s * 0.015, cy = screenRect.midY, t = s * 0.105
    let tri = NSBezierPath()
    tri.move(to: NSPoint(x: cx - t * 0.75, y: cy - t)); tri.line(to: NSPoint(x: cx + t, y: cy))
    tri.line(to: NSPoint(x: cx - t * 0.75, y: cy + t)); tri.close()
    tri.lineJoinStyle = .round; tri.lineWidth = s * 0.03
    ink.setFill(); ink.setStroke(); tri.fill(); tri.stroke()
    // Stand.
    let stand = NSBezierPath(roundedRect: r(0.34, 0.75, 0.58, 0.795), xRadius: s * 0.02, yRadius: s * 0.02)
    NSGradient(starting: cyan.withAlphaComponent(0.9), ending: green.withAlphaComponent(0.9))!.draw(in: stand, angle: 0)
}

// Fire TV tile icon. For sideloaded apps Fire OS ignores the banner and adaptive icons: it takes
// this square icon and centres it on its own grey 16:9 tile. A transparent background lets that
// grey run through, so the tile reads as one full-width picture instead of a box with grey bars.
for (dir, px) in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)] {
    render(px, px, to: "drawable-\(dir)/ic_tile.png") { w, _ in
        mark(NSRect(x: 0, y: 0, width: w, height: w).insetBy(dx: -w * 0.08, dy: -w * 0.08))
    }
}

// TV banner (320×180 at xhdpi): mark plus the app name in Montserrat (OFL), the xelerate wordmark face.
func montserrat(_ size: CGFloat, _ weight: NSFont.Weight) -> NSFont {
    let base = NSFont(name: "Montserrat", size: size) ?? NSFont.systemFont(ofSize: size)
    // Montserrat is a variable font: set its 'wght' axis (0x77676874) directly.
    let wght: CGFloat = weight == .bold ? 700 : weight == .semibold ? 600 : 400
    let d = base.fontDescriptor.addingAttributes([NSFontDescriptor.AttributeName(rawValue: "NSCTFontVariationAttribute"): [0x77676874: wght]])
    return NSFont(descriptor: d, size: size) ?? base
}
for (dir, scale) in [("xhdpi", 1.0), ("xxhdpi", 1.5), ("xxxhdpi", 2.0)] as [(String, CGFloat)] {
    render(Int(320 * scale), Int(180 * scale), to: "drawable-\(dir)/banner.png") { w, h in
        let u = w / 320
        navyBackground(NSRect(x: 0, y: 0, width: w, height: h), radius: 0)
        // Brand accent bands, echoing the xelerate slides.
        let band = NSBezierPath()
        band.move(to: NSPoint(x: 0, y: 0)); band.line(to: NSPoint(x: 70 * u, y: 0))
        band.line(to: NSPoint(x: 0, y: 42 * u)); band.close()
        NSGradient(starting: cyan.withAlphaComponent(0.85), ending: green.withAlphaComponent(0.85))!.draw(in: band, angle: 0)
        let band2 = NSBezierPath()
        band2.move(to: NSPoint(x: w, y: h)); band2.line(to: NSPoint(x: w - 90 * u, y: h))
        band2.line(to: NSPoint(x: w, y: h - 54 * u)); band2.close()
        NSGradient(starting: cyan.withAlphaComponent(0.85), ending: green.withAlphaComponent(0.85))!.draw(in: band2, angle: 0)

        mark(NSRect(x: 14 * u, y: 28 * u, width: 124 * u, height: 124 * u))
        let title = NSAttributedString(string: "XC TV Wall", attributes: [
            .font: montserrat(31 * u, .bold), .foregroundColor: NSColor.white, .kern: -0.3 * u])
        title.draw(at: NSPoint(x: 140 * u, y: 64 * u))
        let sub = NSAttributedString(string: "ONBOARDING", attributes: [
            .font: montserrat(11 * u, .semibold), .foregroundColor: hex(0x5CF5C8), .kern: 2.6 * u])
        sub.draw(at: NSPoint(x: 142 * u, y: 103 * u))
    }
}
print("done")
