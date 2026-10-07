export const CROP_SIZE = 260;
export type Position = { x: number; y: number };
export function cropScale(width: number, height: number, zoom: number) {
  return Math.max(CROP_SIZE / width, CROP_SIZE / height) * zoom;
}
export function constrainPosition(
  position: Position,
  width: number,
  height: number,
  zoom: number,
): Position {
  const scale = cropScale(width, height, zoom);
  const maxX = Math.max(0, (width * scale - CROP_SIZE) / 2);
  const maxY = Math.max(0, (height * scale - CROP_SIZE) / 2);
  return {
    x: maxX === 0 ? 0 : Math.max(-maxX, Math.min(maxX, position.x)),
    y: maxY === 0 ? 0 : Math.max(-maxY, Math.min(maxY, position.y)),
  };
}
export async function exportCrop(
  image: HTMLImageElement,
  zoom: number,
  position: Position,
): Promise<Blob> {
  const canvas = document.createElement("canvas");
  canvas.width = canvas.height = 512;
  const context = canvas.getContext("2d");
  if (!context)
    throw new Error("Image editing is unavailable in this browser.");
  const ratio = canvas.width / CROP_SIZE;
  const scale = cropScale(image.naturalWidth, image.naturalHeight, zoom);
  const safe = constrainPosition(
    position,
    image.naturalWidth,
    image.naturalHeight,
    zoom,
  );
  context.fillStyle = "#fff";
  context.fillRect(0, 0, 512, 512);
  context.drawImage(
    image,
    (CROP_SIZE / 2 + safe.x - (image.naturalWidth * scale) / 2) * ratio,
    (CROP_SIZE / 2 + safe.y - (image.naturalHeight * scale) / 2) * ratio,
    image.naturalWidth * scale * ratio,
    image.naturalHeight * scale * ratio,
  );
  return new Promise((resolve, reject) =>
    canvas.toBlob(
      (blob) =>
        blob
          ? resolve(blob)
          : reject(
              new Error(
                "Unable to prepare your photo. Please choose another image.",
              ),
            ),
      "image/jpeg",
      0.92,
    ),
  );
}
