import React from 'react';
import { Monitor, Link as LinkIcon, LogOut } from 'lucide-react';
import './HomeScreen.css';

const HomeScreen = ({ onUnpair }) => {
    const connectedBrowsers = [];
    const history = [];

    return (
        <div className="home-screen">
            <header className="home-header">
                <div>
                    <h2>Supa<span>Phone</span></h2>
                    <p className="status-text"><span className="status-dot"></span> Securely Connected</p>
                </div>
                <button className="icon-btn" onClick={onUnpair} title="Unpair Device">
                    <LogOut size={20} />
                </button>
            </header>

            <section className="dashboard-section">
                <h3>Active Links</h3>
                <div className="connections-list">
                    {connectedBrowsers.length === 0 && (
                        <div className="connection-card">
                            <div className="conn-icon">
                                <Monitor size={20} />
                            </div>
                            <div className="conn-info">
                                <h4>No paired browsers</h4>
                                <p>Pair from extension and refresh this app state.</p>
                            </div>
                        </div>
                    )}
                </div>
            </section>

            <section className="dashboard-section flex-1 history-section">
                <div className="section-header">
                    <h3>Recent Pushes</h3>
                    <button className="text-btn">Clear</button>
                </div>

                <div className="history-list">
                    {history.length === 0 && (
                        <div className="history-item">
                            <div className="history-icon link">
                                <LinkIcon size={16} />
                            </div>
                            <div className="history-content">
                                <p className="history-text">No recent pushes</p>
                                <span className="history-time">Backend data will appear here</span>
                            </div>
                        </div>
                    )}
                </div>
            </section>
        </div>
    );
};

export default HomeScreen;
