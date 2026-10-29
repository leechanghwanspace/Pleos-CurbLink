/* Driver eye-level renderer. Canvas 2D deliberately keeps navigation visible without WebGL. */
function DriverViewport({ frame, recommendation, playing }) {
  const canvas = React.useRef(null);
  const latest = React.useRef({ frame, recommendation, playing });
  latest.current = { frame, recommendation, playing };
  React.useEffect(() => {
    const el = canvas.current, ctx = el.getContext('2d');
    if (!ctx) return;
    let raf, w = 1, h = 1, lastKey = '', visualDistance = 600, visualLane = 2;
    const resize = () => {
      const box = el.getBoundingClientRect();
      w = Math.max(320, box.width); h = Math.max(240, box.height);
      const ratio = Math.min(devicePixelRatio || 1, 2);
      el.width = w * ratio; el.height = h * ratio; ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    };
    const observer = new ResizeObserver(resize); observer.observe(el); resize();
    const polygon = (points, fill) => {
      ctx.beginPath(); points.forEach((p, i) => i ? ctx.lineTo(p[0],p[1]) : ctx.moveTo(p[0],p[1]));
      ctx.closePath(); ctx.fillStyle = fill; ctx.fill();
    };
    const gradient = (y1,y2,a,b) => { const g=ctx.createLinearGradient(0,y1,0,y2);g.addColorStop(0,a);g.addColorStop(1,b);return g; };
    const draw = () => {
      raf = requestAnimationFrame(draw);
      const {frame:input,recommendation:r} = latest.current;
      const targetDistance = input?.distance ?? 600;
      if (Math.abs(targetDistance - visualDistance) > 100) visualDistance = targetDistance;
      visualDistance += (targetDistance - visualDistance) * .24;
      visualLane += ((input?.lanePosition ?? 2) - visualLane) * .24;
      if (Math.abs(visualDistance-targetDistance)<.001) visualDistance=targetDistance;
      if (Math.abs(visualLane-(input?.lanePosition??2))<.001) visualLane=input?.lanePosition??2;
      const f = input ? {...input,distance:visualDistance,lanePosition:visualLane} : null;
      const key = [w,h,f?.distance,f?.lanePosition,r?.lane].join('/');
      if (key === lastKey) return;
      lastKey = key;
      const eyeX = ((f?.lanePosition ?? 2) - 2.5) * 3.5 + (f ? Math.max(0,1-f.distance/25)*2.8 : 0);
      const s = f ? 600-f.distance : 0;
      const horizon = h*.36, focal = w*.76;
      const p = (x,y,z) => [w*.5+(x-eyeX)*focal/Math.max(z,.7), horizon+(1.45-y)*focal/Math.max(z,.7)];
      const quad = (x1,x2,z1,z2,y,color) => polygon([p(x1,y,z1),p(x2,y,z1),p(x2,y,z2),p(x1,y,z2)],color);
      ctx.fillStyle=gradient(0,horizon,'#68899d','#d9e1d9');ctx.fillRect(0,0,w,h);
      // Distant skyline and atmospheric haze.
      for(let i=0;i<28;i++) { const x=i*w/27;const bh=20+(Math.sin(i*23)+1)*35;ctx.fillStyle=i%2?'#8c9c9f':'#9eacac';ctx.fillRect(x,horizon-bh,w/24,bh); }
      ctx.fillStyle=gradient(horizon,h,'#7b8583','#555e5d');ctx.fillRect(0,horizon,w,h);
      quad(-7,7,1,600,0,gradient(horizon,h,'#777f80','#292f33'));
      // Granular asphalt strips in perspective; no flashing random noise.
      for(let z=2;z<250;z+=2) { const zz=z+(s%2);quad(-7,7,zz,zz+.025,0,'rgba(230,235,240,.05)'); }
      quad(-8.7,-7,1,600,.13,'#a6aaa6');quad(7,10,1,600,.13,'#b2b4ab');
      quad(-7.1,-6.98,1,600,.015,'#e8db9a');quad(6.98,7.1,1,600,.015,'#e8db9a');
      for(let lane=1;lane<4;lane++) for(let z=0;z<420;z+=12) {
        const zz=z-((s%12)+12)%12;if(zz>1)quad(-7+lane*3.5-.055,-7+lane*3.5+.055,zz,zz+5,0.02,'#e4e6df');
      }
      // Continuous route ribbon projected on the road surface.
      if(r?.lane) {
        const tx=(r.lane-2.5)*3.5;
        for(let z=3;z<130;z+=2) {const x=eyeX+(tx-eyeX)*Math.min(1,z/50);quad(x-.25,x+.25,z,z+1.4,.025,'rgba(41,218,190,.32)');}
      }
      // Selected curb bay is at the actual route end, not attached to the camera.
      const bayZ=f?.distance ?? 200;
      if(bayZ<220) {
        const z=Math.max(2,bayZ);
        quad(7.15,10.3,Math.max(1,z-8),z+12,.15,f?.mode==='SHORT_STOP'?'#b59b45':'#387c67');
        quad(7.15,7.28,Math.max(1,z-8),z+12,.16,'#a6f2bf');
        const label=p(9,2.8,z+8);ctx.fillStyle='#153e35';ctx.fillRect(label[0]-45,label[1]-24,90,30);ctx.fillStyle='white';ctx.font='bold 13px sans-serif';ctx.fillText(f?.zone.id || 'A-3',label[0]-18,label[1]-4);
      }
      // Crosswalk before the destination, terminating before the stopping bay.
      const crossing=bayZ-45;
      if(crossing>3 && crossing<180)for(let x=-6.5;x<7;x+=1.1)quad(x,x+.65,crossing,crossing+3,0.025,'#e6e5d9');
      // Street frontage: perspective facades, window grids, awnings and trees.
      for(let i=22;i>=0;i--) {
        const block=i+Math.floor(s/23), z=12+i*23-(s%23), height=10+(block%5)*3;
        if (z < 2) continue;
        for(const side of [-1,1]) {
          const front=side*11, back=side*(18+i%4);
          polygon([p(front,0,z),p(back,0,z),p(back,height,z),p(front,height,z)],side===1?'#b8b3a5':'#8b969b');
          polygon([p(front,0,z),p(front,0,z+18),p(front,height,z+18),p(front,height,z)],side===1?'#929b99':'#77868c');
          for(let floor=1;floor<height/2.6;floor++)for(let row=0;row<5;row++) {
            polygon([p(front-.03*side,floor*2.6,z+1+row*3),p(front-.03*side,floor*2.6,z+2.7+row*3),p(front-.03*side,floor*2.6+1.4,z+2.7+row*3),p(front-.03*side,floor*2.6+1.4,z+1+row*3)],(floor+row+block)%4===0?'#bfc7bd':'#435b65');
          }
          // Ground-floor glass shopfronts, awnings and street-facing signs.
          polygon([p(front-.05*side,.25,z+1),p(front-.05*side,.25,z+15),p(front-.05*side,2.1,z+15),p(front-.05*side,2.1,z+1)],'#385660');
          polygon([p(front-side*.8,2.3,z+1),p(front-side*.8,2.3,z+15),p(front,2.6,z+15),p(front,2.6,z+1)],block%2?'#596c61':'#8f8573');
          for(let seam=0;seam<5;seam++){
            const a=p(side*7.1,.14,z+seam*4),b=p(side*10.7,.14,z+seam*4);
            ctx.strokeStyle='rgba(48,60,60,.22)';ctx.lineWidth=.7;ctx.beginPath();ctx.moveTo(...a);ctx.lineTo(...b);ctx.stroke();
          }
          const tree=p(side*8.7,2.8,z+5), base=p(side*8.7,0,z+5), radius=Math.min(90,focal/(z+5)*1.5);
          ctx.strokeStyle='#65594b';ctx.lineWidth=Math.max(1,focal/(z+5)*.15);ctx.beginPath();ctx.moveTo(...base);ctx.lineTo(...tree);ctx.stroke();
          const leaf=ctx.createRadialGradient(tree[0]-radius*.3,tree[1]-radius*.3,1,tree[0],tree[1],radius);leaf.addColorStop(0,'#7b906a');leaf.addColorStop(1,'#354b3e');// Overlapping irregular foliage masses avoid geometric sphere-shaped trees.
          for(let clump=0;clump<24;clump++){
            const angle=clump*2.39996,extent=Math.sqrt(clump/24);
            const tx=tree[0]+Math.cos(angle)*radius*.65*extent,ty=tree[1]+Math.sin(angle)*radius*.8*extent;
            const rr=radius*(.25+.12*Math.sin(clump*17+block));
            const foliage=ctx.createRadialGradient(tx-rr*.3,ty-rr*.4,0,tx,ty,Math.max(.1,rr));
            foliage.addColorStop(0,clump%3?'#6c815a':'#83956c');foliage.addColorStop(1,'#3b5140');
            ctx.fillStyle=foliage;ctx.beginPath();ctx.arc(tx,ty,Math.max(.1,rr),0,Math.PI*2);ctx.fill();
          }
          const lamp=p(side*7.8,5.8,z+15), foot=p(side*7.8,0,z+15);ctx.strokeStyle='#536367';ctx.lineWidth=Math.max(1,focal/(z+15)*.06);ctx.beginPath();ctx.moveTo(...foot);ctx.lineTo(...lamp);ctx.lineTo(lamp[0]-side*radius*.45,lamp[1]);ctx.stroke();
        }
      }
      // Sidewalk pedestrians remain outside the carriageway in this demo.
      for (const [side,zz] of [[1,32],[-1,64],[1,91]]) {
        const z=zz+(s%8),head=p(side*9,1.65,z),foot=p(side*9,0,z),scale=focal/z;
        ctx.fillStyle='#bfa58b';ctx.beginPath();ctx.arc(head[0],head[1],scale*.12,0,Math.PI*2);ctx.fill();
        ctx.strokeStyle=side>0?'#485c74':'#776458';ctx.lineWidth=scale*.23;ctx.beginPath();ctx.moveTo(head[0],head[1]+scale*.16);ctx.lineTo(foot[0],foot[1]-scale*.7);ctx.stroke();
        ctx.strokeStyle='#3e4549';ctx.lineWidth=scale*.09;ctx.beginPath();ctx.moveTo(foot[0],foot[1]-scale*.7);ctx.lineTo(foot[0]-scale*.14,foot[1]);ctx.moveTo(foot[0],foot[1]-scale*.7);ctx.lineTo(foot[0]+scale*.14,foot[1]);ctx.stroke();
      }
      // Traffic retains relative separation in the fixed, conflict-free demo.
      [{lane:1,z:42,c:'#e0e1dc'},{lane:3,z:65,c:'#536c7d'},{lane:2,z:100,c:'#c4c9c9'},{lane:4,z:155,c:'#69787c'}].reverse().forEach(car=>{
        const z=car.z + Math.sin((f?.elapsed||0)*.09+car.lane)*3, x=(car.lane-2.5)*3.5;
        const a=p(x,0,z), scale=focal/z, cw=1.8*scale,ch=1.35*scale;
        ctx.fillStyle='rgba(0,0,0,.25)';ctx.beginPath();ctx.ellipse(a[0],a[1],cw*.65,ch*.15,0,0,Math.PI*2);ctx.fill();
        const cg=ctx.createLinearGradient(0,a[1]-ch,0,a[1]);cg.addColorStop(0,car.c);cg.addColorStop(1,'#343e43');
        ctx.fillStyle='#20262a';ctx.fillRect(a[0]-cw*.49,a[1]-ch*.3,cw*.18,ch*.32);ctx.fillRect(a[0]+cw*.31,a[1]-ch*.3,cw*.18,ch*.32);
        polygon([[a[0]-cw*.5,a[1]-ch*.1],[a[0]-cw*.5,a[1]-ch*.55],[a[0]-cw*.34,a[1]-ch],[a[0]+cw*.34,a[1]-ch],[a[0]+cw*.5,a[1]-ch*.55],[a[0]+cw*.5,a[1]-ch*.1]],cg);
        polygon([[a[0]-cw*.3,a[1]-ch*.9],[a[0]+cw*.3,a[1]-ch*.9],[a[0]+cw*.39,a[1]-ch*.58],[a[0]-cw*.39,a[1]-ch*.58]],'#273f4b');
        ctx.fillStyle='#f18378';ctx.fillRect(a[0]-cw*.44,a[1]-ch*.38,cw*.23,ch*.09);ctx.fillRect(a[0]+cw*.21,a[1]-ch*.38,cw*.23,ch*.09);
      });
      // Windshield pillars and a curved dashboard establish the user's eye position.
      polygon([[0,0],[w*.035,0],[w*.13,h*.88],[0,h]],gradient(0,h,'#283138','#121a20'));
      polygon([[w,0],[w*.965,0],[w*.87,h*.88],[w,h]],gradient(0,h,'#283138','#121a20'));
      ctx.fillStyle=gradient(h*.85,h,'#344047','#0b1118');ctx.beginPath();ctx.moveTo(0,h);ctx.lineTo(0,h*.92);ctx.quadraticCurveTo(w*.5,h*.78,w,h*.92);ctx.lineTo(w,h);ctx.fill();
      ctx.strokeStyle='#45525b';ctx.lineWidth=2;ctx.beginPath();ctx.moveTo(0,h*.94);ctx.quadraticCurveTo(w*.5,h*.81,w,h*.94);ctx.stroke();
      ctx.fillStyle='#13212a';ctx.fillRect(w*.41,h*.91,w*.18,h*.09);ctx.fillStyle='#a8d7d7';ctx.font=`500 ${Math.max(14,w*.018)}px sans-serif`;ctx.textAlign='center';ctx.fillText(`${Math.round(f?.speedKph||0)}  km/h`,w*.5,h*.965);ctx.textAlign='left';
    };draw();
    return()=>{cancelAnimationFrame(raf);observer.disconnect();};
  }, []);
  return <div className="driver-scene">
    <canvas ref={canvas} aria-label="운전자 눈높이의 도시 도로 주행 시뮬레이션" />
    <div className="driver-view-label">DRIVER VIEW <span>운전자 시점 · 가상 도로</span></div>
    <div className="driver-navigation"><span className="driver-turn">{frame?.stopped?'Ⓟ':frame && frame.distance < (frame.mode==='PARKING'?180:450)?'↗':'↑'}</span><div><strong>{frame?.stopped?'목적지에 도착했습니다':`${Math.ceil(frame?.distance??600)} m 앞 · ${frame && frame.distance<180?'진입 준비':'경로 유지'}`}</strong><small>{frame?.zone.label || '센트럴몰 동문 · 시나리오를 선택하세요'}</small></div></div>
    <div className="driver-minimap" aria-label="보조 경로 지도">
      <div>중앙로 → 동문 진입로 <span>N ↑</span></div>
      <svg viewBox="0 0 200 170" role="img" aria-label="현재 위치와 목적지 연결 경로">
        <rect width="200" height="170" fill="#17252e" />
        {[0,1,2].map(i=><g key={i}><rect x={20+i*62} y="12" width="40" height="45" rx="5" fill="#263b45"/><rect x={20+i*62} y="90" width="40" height="58" rx="5" fill="#263b45"/></g>)}
        <path d="M 75 170 V 0 M 0 75 H 200 M 175 0 V 170" stroke="#4c606b" strokeWidth="16" />
        <path d="M 75 155 V 75 H 175 V 45" stroke="#5eead4" strokeWidth="5" fill="none" />
        <circle cx="175" cy="45" r="8" fill={frame?.mode==='SHORT_STOP'?'#fbbf24':'#6ee7b7'} />
        <path transform={`translate(${frame && frame.distance<60 ? 75+(1-frame.distance/60)*100 : 75},${frame && frame.distance<60 ? 75 : 155-(1-(frame?.distance??600)/600)*80})`} d="M 0 -10 L 8 9 L 0 5 L -8 9 Z" fill="white" stroke="#13b8a0" strokeWidth="2" />
      </svg>
      <small>{frame?.stopped?'도착':`약 ${Math.max(1,Math.ceil((frame?.distance??600)/8/60))}분 · ${Math.ceil(frame?.distance??600)}m`}</small>
    </div>
    <div className="driver-lanes">{[1,2,3,4].map(l=><span key={l} className={recommendation?.lane===l?'recommended':''}>{['↰','↑','↑','↱'][l-1]}<small>{l}차선</small></span>)}</div>
    <div className="driver-caption">{frame?.stopped?(frame.mode==='PICKUP'?'정차 완료 · 차량을 확인한 후 인계를 진행하세요':frame.mode==='PARKING'?'주차장 진입점 도착 · 정차 완료':'단기 정차 구역 도착 · 정차 완료'):frame && frame.distance<80?'보행자 및 우측 사각지대 확인':playing?'경로 안내 중 · 안전 간격 확보된 가상 교통':frame?'일시 정지 · 이어서 재생을 누르면 안내가 계속됩니다':'미리보기 · 프리셋을 눌러 주행 시작'}</div>
  </div>;
}
