import React, { useState } from 'react';
import PairingScreen from './screens/PairingScreen';
import HomeScreen from './screens/HomeScreen';

function App() {
  const [isPaired, setIsPaired] = useState(false);

  return (
    <div className="mobile-app-container">
      {isPaired ? (
        <HomeScreen onUnpair={() => setIsPaired(false)} />
      ) : (
        <PairingScreen onPaired={() => setIsPaired(true)} />
      )}
    </div>
  );
}

export default App;
