import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export default function Home() {
  const [name, setName] = useState('');
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!name) return;
    
    // Call API to get encoded URL (Requirement 2)
    try {
      const res = await fetch(`/create/shop/${name}`);
      const data = await res.json();
      // data.url is full url, we just need the path or navigate to encoded
      navigate(`/shop/${data.encoded}`);
    } catch (err) {
      console.error(err);
      alert('Error creating shop URL');
    }
  };

  return (
    <div className="container login-container">
      <div className="card login-card">
        <h1>TR:ADE 로그인</h1>
        <form onSubmit={handleSubmit}>
          <input 
            type="text" 
            placeholder="마인크래프트 닉네임" 
            value={name} 
            onChange={e => setName(e.target.value)} 
          />
          <button className="btn btn-primary" type="submit">상점 입장</button>
        </form>
      </div>
    </div>
  );
}
