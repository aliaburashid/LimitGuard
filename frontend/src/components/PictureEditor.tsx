import { useEffect, useRef, useState, type PointerEvent } from "react";
import { Move, Minus, Plus, RotateCcw } from "lucide-react";
import { Modal, ErrorBox } from "./ui";
import {
  CROP_SIZE,
  constrainPosition,
  cropScale,
  exportCrop,
  type Position,
} from "../utils/crop";
export function PictureEditor({
  file,
  onClose,
  onSave,
}: {
  file: File;
  onClose: () => void;
  onSave: (blob: Blob) => Promise<void>;
}) {
  const [source, setSource] = useState("");
  const [dimensions, setDimensions] = useState({ width: 0, height: 0 });
  const [zoom, setZoom] = useState(1);
  const [position, setPosition] = useState<Position>({ x: 0, y: 0 });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const image = useRef<HTMLImageElement>(null);
  const drag = useRef<{
    pointer: number;
    x: number;
    y: number;
    origin: Position;
  } | null>(null);
  useEffect(() => {
    const url = URL.createObjectURL(file);
    setSource(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);
  function move(next: Position, nextZoom = zoom) {
    if (!dimensions.width || !dimensions.height) return;
    setPosition(
      constrainPosition(next, dimensions.width, dimensions.height, nextZoom),
    );
  }
  function changeZoom(next: number) {
    setZoom(next);
    move(
      { x: (position.x * next) / zoom, y: (position.y * next) / zoom },
      next,
    );
  }
  function pointerMove(event: PointerEvent<HTMLDivElement>) {
    if (!drag.current || drag.current.pointer !== event.pointerId || busy)
      return;
    move({
      x: drag.current.origin.x + event.clientX - drag.current.x,
      y: drag.current.origin.y + event.clientY - drag.current.y,
    });
  }
  async function save() {
    if (busy || !image.current || !dimensions.width) return;
    setBusy(true);
    setError("");
    try {
      await onSave(await exportCrop(image.current, zoom, position));
      onClose();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  const scale = dimensions.width
    ? cropScale(dimensions.width, dimensions.height, zoom)
    : 1;
  return (
    <Modal
      title="Adjust profile photo"
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <p className="muted">
        Drag your photo to position it inside the circle. Zoom in for a closer
        crop.
      </p>
      <div className="picture-editor">
        <div
          className={`crop-window${busy ? " saving" : ""}`}
          role="group"
          aria-label="Photo position"
          tabIndex={0}
          aria-describedby="crop-instructions"
          onKeyDown={(e) => {
            const steps: Record<string, Position> = {
              ArrowLeft: { x: -8, y: 0 },
              ArrowRight: { x: 8, y: 0 },
              ArrowUp: { x: 0, y: -8 },
              ArrowDown: { x: 0, y: 8 },
            };
            if (steps[e.key] && !busy) {
              e.preventDefault();
              move({
                x: position.x + steps[e.key].x,
                y: position.y + steps[e.key].y,
              });
            }
          }}
          onPointerDown={(e) => {
            if (busy || !dimensions.width || !e.isPrimary) return;
            e.currentTarget.focus();
            e.currentTarget.setPointerCapture(e.pointerId);
            drag.current = {
              pointer: e.pointerId,
              x: e.clientX,
              y: e.clientY,
              origin: position,
            };
          }}
          onPointerMove={pointerMove}
          onPointerUp={() => {
            drag.current = null;
          }}
          onPointerCancel={() => {
            drag.current = null;
          }}
          onLostPointerCapture={() => {
            drag.current = null;
          }}
        >
          {source && (
            <img
              ref={image}
              src={source}
              alt="Photo crop preview"
              draggable={false}
              onLoad={(e) => {
                setDimensions({
                  width: e.currentTarget.naturalWidth,
                  height: e.currentTarget.naturalHeight,
                });
              }}
              onError={() =>
                setError(
                  "This image could not be opened. Choose a JPG, PNG or WebP photo.",
                )
              }
              style={{
                width: dimensions.width * scale,
                height: dimensions.height * scale,
                transform: `translate(calc(-50% + ${position.x}px), calc(-50% + ${position.y}px))`,
              }}
            />
          )}
          <span className="crop-grid" aria-hidden="true" />
        </div>
        <span id="crop-instructions" className="crop-hint">
          <Move size={15} />
          Drag to reposition · Arrow keys also work
        </span>
        <label className="zoom-control">
          <Minus size={16} />
          <span className="sr-only">Photo zoom</span>
          <input
            type="range"
            min="1"
            max="3"
            step="0.01"
            value={zoom}
            disabled={busy || !dimensions.width}
            onChange={(e) => changeZoom(Number(e.target.value))}
          />
          <Plus size={16} />
          <output>{Math.round(zoom * 100)}%</output>
        </label>
        <button
          className="text-button"
          disabled={busy}
          onClick={() => {
            setZoom(1);
            setPosition({ x: 0, y: 0 });
          }}
        >
          <RotateCcw size={14} />
          Reset position
        </button>
      </div>
      {error && <ErrorBox message={error} />}
      <div className="modal-actions">
        <button disabled={busy} onClick={onClose}>
          Cancel
        </button>
        <button
          className="primary"
          disabled={busy || !dimensions.width}
          onClick={save}
        >
          {busy ? "Saving photo…" : "Save photo"}
        </button>
      </div>
    </Modal>
  );
}
