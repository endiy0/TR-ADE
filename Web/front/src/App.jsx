import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Home from './pages/Home';
import Shop from './pages/Shop';
import Admin from './pages/Admin';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<div />} /> {/* Empty */}
        <Route path="/create" element={<Home />} />
        <Route path="/shop/:playerEncoded" element={<Shop />} />
        <Route path="/admin" element={<Admin />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
