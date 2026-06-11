import { useState } from 'react'

const OPTIONS = [7, 15, 30, 60, 90]

export default function SyncDaysModal({ reason, onConfirm, onCancel }) {
  const [selected, setSelected] = useState(30)

  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div className="modal-box" onClick={e => e.stopPropagation()}>
        <h2 className="modal-title">How far back should we search?</h2>
        <p className="modal-sub">{reason}</p>

        <div className="modal-options">
          {OPTIONS.map(d => (
            <button
              key={d}
              className={`modal-day-opt${selected === d ? ' active' : ''}`}
              onClick={() => setSelected(d)}
            >
              {d} days
            </button>
          ))}
        </div>

        <div className="modal-actions">
          <button className="btn-cancel" onClick={onCancel}>Cancel</button>
          <button className="btn-confirm" onClick={() => onConfirm(selected)}>
            Sync last {selected} days
          </button>
        </div>
      </div>
    </div>
  )
}
