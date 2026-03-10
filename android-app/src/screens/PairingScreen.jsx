import React, { useState } from 'react';
import { QrCode, Smartphone, Zap } from 'lucide-react';
import './PairingScreen.css';

const PairingScreen = ({ onPaired }) => {
    const [activeTab, setActiveTab] = useState('qr');

    return (
        <div className="pairing-screen">
            <header className="pairing-header">
                <h1>Supa<span>Phone</span></h1>
                <p>Connect to your browser</p>
            </header>

            <div className="pairing-tabs">
                <button
                    className={`tab-btn ${activeTab === 'qr' ? 'active' : ''}`}
                    onClick={() => setActiveTab('qr')}
                >
                    <QrCode size={18} /> Scan QR
                </button>
                <button
                    className={`tab-btn ${activeTab === 'code' ? 'active' : ''}`}
                    onClick={() => setActiveTab('code')}
                >
                    <Smartphone size={18} /> Enter Code
                </button>
            </div>

            <div className="pairing-content">
                {activeTab === 'qr' ? (
                    <div className="qr-scanner-panel">
                        <div className="scanner-frame">
                            <div className="scanner-corner top-left"></div>
                            <div className="scanner-corner top-right"></div>
                            <div className="scanner-corner bottom-left"></div>
                            <div className="scanner-corner bottom-right"></div>
                            <div className="scanner-laser"></div>
                        </div>
                        <p>Point camera at the QR code on your desktop</p>
                    </div>
                ) : (
                    <div className="code-entry-panel">
                        <p>Enter the 6-digit code shown in the extension</p>
                        <div className="code-inputs">
                            <input type="text" maxLength={1} placeholder="0" className="code-box" />
                            <input type="text" maxLength={1} placeholder="0" className="code-box" />
                            <input type="text" maxLength={1} placeholder="0" className="code-box" />
                            <div className="code-dash">-</div>
                            <input type="text" maxLength={1} placeholder="0" className="code-box" />
                            <input type="text" maxLength={1} placeholder="0" className="code-box" />
                            <input type="text" maxLength={1} placeholder="0" className="code-box" />
                        </div>
                    </div>
                )}
            </div>

            <div className="pairing-actions">
                <button className="btn-primary" onClick={onPaired}>
                    <Zap size={20} />
                    {activeTab === 'qr' ? 'Open Camera' : 'Connect with Code'}
                </button>
            </div>
        </div>
    );
};

export default PairingScreen;
