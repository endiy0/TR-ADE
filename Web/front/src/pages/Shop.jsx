import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';

export default function Shop() {
  const { playerEncoded } = useParams();
  const [playerName, setPlayerName] = useState('');
  const [products, setProducts] = useState([]);
  const [debts, setDebts] = useState([]);
  const [orderId, setOrderId] = useState(null);
  const [orderStatus, setOrderStatus] = useState(null);
  const [message, setMessage] = useState('');

  const NAME_MAP = {
    'GOLD_INGOT': '금괴',
    'IRON_INGOT': '철괴',
    'DIAMOND': '다이아몬드',
    'TP_TICKET': 'TP권',
    'INVSAVE_TICKET': '인벤세이브권',
    'SHULKER_BOX': '셜커 상자',
    'VILLAGER_SPAWN_EGG': '주민 생성 알',
    'ENDER_PEARL': '엔더 진주',
    'MENDING_BOOK': '수선 마법부여 책',
    'COBBLESTONE': '조약돌',
    'EMERALD': '에메랄드',
    'NETHERITE_INGOT': '네더라이트 주괴',
    'PAPER': '종이'
  };

  const t = (key) => NAME_MAP[key] || key;

  useEffect(() => {
    // Decode base64url
    try {
      let str = playerEncoded.replace(/-/g, '+').replace(/_/g, '/');
      while (str.length % 4) str += '=';
      setPlayerName(atob(str));
    } catch (e) {
      setPlayerName('Invalid Player');
    }

    fetch('/api/products').then(res => res.json()).then(setProducts);
  }, [playerEncoded]);

  useEffect(() => {
    if (playerName && playerName !== 'Invalid Player') {
        const fetchDebts = () => fetch(`/api/debts/${playerName}`).then(res => res.json()).then(setDebts);
        fetchDebts();
        const interval = setInterval(fetchDebts, 3000); // Poll every 3 seconds
        return () => clearInterval(interval);
    }
  }, [playerName]);

  useEffect(() => {
    if (!orderId) return;
    const interval = setInterval(() => {
        fetch(`/api/orders/${orderId}`)
            .then(res => res.json())
            .then(data => {
                setOrderStatus(data.status);
                if (data.status === 'SUCCEEDED' || data.status === 'FAILED') {
                    setMessage(data.message || (data.status === 'SUCCEEDED' ? '구매 성공!' : '구매 실패'));
                    clearInterval(interval);
                    setTimeout(() => { 
                        setOrderId(null); 
                        setOrderStatus(null); 
                        setMessage('');
                        // Refresh debts on success
                        if (data.status === 'SUCCEEDED') {
                             fetch(`/api/debts/${playerName}`).then(res => res.json()).then(setDebts);
                        }
                    }, 5000);
                }
            });
    }, 1000);
    return () => clearInterval(interval);
  }, [orderId, playerName]);

  const buy = async (productId, method) => {
    if (orderId) return; // Busy
    if (method === 'CREDIT') {
        if (!confirm('경고: 신용 결제는 2일(40분) 내에 상환해야 합니다. 상환하지 못할 경우 인벤토리 세이브 권한이 있더라도 사망 시 아이템이 소멸됩니다. 진행하시겠습니까?')) return;
    }

    try {
        const res = await fetch('/api/orders', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ playerName, productId, paymentMethod: method })
        });
        const data = await res.json();
        setOrderId(data.orderId);
        setOrderStatus('PENDING');
        setMessage('처리 중...');
    } catch (e) {
        alert('주문 실패');
    }
  };

  return (
    <div className="container">
      <h1>환영합니다, {playerName}</h1>
      {message && <div className={`status-msg ${orderStatus === 'FAILED' ? 'error' : 'success'}`}>{message}</div>}
      
      {debts.length > 0 && (
        <div className="debt-section">
            <h2>미상환 빚 (자동 상환됨)</h2>
            <ul className="debt-list">
                {debts.map(d => (
                    <li key={d.id} className={d.status === 'OVERDUE' ? 'debt-overdue' : ''}>
                        <span className="debt-item">{t(d.currency_material)} x {d.remaining_amount}</span>
                        <span className="debt-status">{d.status === 'OVERDUE' ? '연체됨 (사망 시 페널티)' : '상환 중'}</span>
                    </li>
                ))}
            </ul>
        </div>
      )}

      <div className="product-grid">
      {products.map(p => (
        <div key={p.product_id} className="card">
            <h3>{t(p.product_id)}</h3>
            <p>가격: {p.currency_amount} x {t(p.currency_material)}</p>
            <button className="btn btn-primary" disabled={!!orderId} onClick={() => buy(p.product_id, 'IMMEDIATE')}>
                즉시 구매
            </button>
            <button className="btn btn-secondary" disabled={!!orderId} onClick={() => buy(p.product_id, 'CREDIT')}>
                신용 구매 (2일)
            </button>
        </div>
      ))}
      </div>
    </div>
  );
}
