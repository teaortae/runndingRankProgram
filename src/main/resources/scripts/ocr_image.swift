import Foundation
import Vision
import ImageIO

func fail(_ message: String) -> Never {
    FileHandle.standardError.write(Data((message + "\n").utf8))
    exit(1)
}

guard CommandLine.arguments.count >= 2 else {
    fail("usage: ocr_image.swift <image-path>")
}

let imagePath = CommandLine.arguments[1]
let imageUrl = URL(fileURLWithPath: imagePath)

guard
    let imageSource = CGImageSourceCreateWithURL(imageUrl as CFURL, nil),
    let image = CGImageSourceCreateImageAtIndex(imageSource, 0, nil)
else {
    fail("이미지를 열 수 없습니다: \(imagePath)")
}

let request = VNRecognizeTextRequest()
request.recognitionLevel = .accurate
request.usesLanguageCorrection = true

do {
    let handler = VNImageRequestHandler(cgImage: image, options: [:])
    try handler.perform([request])

    let observations = request.results as? [VNRecognizedTextObservation] ?? []
    let lines = observations.compactMap { observation in
        observation.topCandidates(1).first?.string.trimmingCharacters(in: .whitespacesAndNewlines)
    }.filter { !$0.isEmpty }

    let payload: [String: Any] = [
        "text": lines.joined(separator: "\n"),
        "lines": lines,
    ]
    let json = try JSONSerialization.data(withJSONObject: payload, options: [])
    guard let output = String(data: json, encoding: .utf8) else {
        fail("OCR 결과를 문자열로 변환하지 못했습니다.")
    }
    print(output)
} catch {
    fail("OCR 실행 실패: \(error.localizedDescription)")
}
