import { useEffect, useState } from 'react';

export default function Admin() {
  const [key, setKey] = useState('');
  const [authorized, setAuthorized] = useState(false);
  const [prices, setPrices] = useState([]);
  const [msg, setMsg] = useState('');

  const login = () => {
    fetch(`/api/admin/prices?key=${key}`)
        .then(res => {
            if (res.ok) return res.json();
            throw new Error('Auth failed');
        })
        .then(data => {
            setPrices(data);
            setAuthorized(true);
        })
        .catch(() => alert('키가 올바르지 않습니다.'));
  };

  const save = async () => {
    try {
        await fetch('/api/admin/prices', {
            method: 'PUT',
            headers: { 
                'Content-Type': 'application/json',
                'X-Admin-Key': key 
            },
            body: JSON.stringify(prices.map(p => ({
                productId: p.product_id,
                currencyMaterial: p.currency_material,
                currencyAmount: parseInt(p.currency_amount)
            })))
        });
        setMsg('저장되었습니다!');
        setTimeout(() => setMsg(''), 2000);
    } catch (e) {
        alert('저장 실패');
    }
  };

  const updatePrice = (id, field, val) => {
    setPrices(prices.map(p => p.product_id === id ? { ...p, [field]: val } : p));
  };

  if (!authorized) {
    return (
        <div className="container login-container">
            <div className="card login-card">
                <h1>관리자 로그인</h1>
                <input type="password" placeholder="Admin Key" value={key} onChange={e => setKey(e.target.value)} />
                <button className="btn btn-primary" onClick={login}>로그인</button>
            </div>
        </div>
    );
  }

  return (
    <div className="container">
      <h1>관리자 패널</h1>
      {msg && <div className="status-msg success">{msg}</div>}
      <button className="btn btn-primary" style={{maxWidth: '200px', margin: '0 auto 2rem auto', display: 'block'}} onClick={save}>모든 변경사항 저장</button>
      
      <div className="product-grid">
      {prices.map(p => (
        <div key={p.product_id} className="card">
            <h3>{p.product_id}</h3>
            <label>화폐: </label>
            <select value={p.currency_material} onChange={e => updatePrice(p.product_id, 'currency_material', e.target.value)}>
                {['COBBLESTONE', 'GOLD_INGOT', 'IRON_INGOT', 'DIAMOND', 'EMERALD', 'NETHERITE_INGOT'].map(m => (
                    <option key={m} value={m}>{m}</option>
                ))}
            </select>
            <label>수량: </label>
            <input type="number" value={p.currency_amount} onChange={e => updatePrice(p.product_id, 'currency_amount', e.target.value)} />
        </div>
      ))}
      </div>
    </div>
  );
}
