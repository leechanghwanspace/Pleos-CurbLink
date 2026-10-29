function MissionMap({ state, surround = false }) {
  const route = state ? (state.routeOverrides||MissionEngine.routes[state.kind]).filter(Boolean).flatMap(r=>r.samples) : [];
  const pose=state?.pose||{x:0,z:0,yaw:0};
  const sx=x=> surround?160+(x-pose.x)*10:35+x*1.5;
  const sy=z=> surround?160-(z-pose.z)*10:290-z*.36;
  return <svg viewBox={surround?'0 0 320 320':'0 0 260 310'} className={surround?'surround-map':'mission-map'} role="img" aria-label={surround?'북쪽 고정 모의 탑뷰 · 차량 주변 위치':'차량 실제 이동 경로 지도'}>
    <rect width="320" height="320" fill="#172b31"/>
    <rect x={sx(22)} y={sy(587)} width={51*(surround?10:1.5)} height={45*(surround?10:.36)} fill="#344951"/>
    {[26,29.5,33,36.5,40,43.5,47,50.5,54,57.5,61].map(x=><rect key={x} x={sx(x-1.5)} y={sy(580)} width={3*(surround?10:1.5)} height={10*(surround?10:.36)} fill={x===40?'#337c66':'none'} stroke={x===40?'#8effcf':'#91a2a3'} strokeWidth={surround?1.5:.5}/>)}
    <path d={`M ${sx(0)} ${sy(0)} L ${sx(0)} ${sy(720)} M ${sx(0)} ${sy(600)} L ${sx(112)} ${sy(600)} L ${sx(112)} ${sy(635)} M ${sx(65)} ${sy(600)} L ${sx(65)} ${sy(640)} L ${sx(87)} ${sy(640)} L ${sx(87)} ${sy(600)} M ${sx(0)} ${sy(552)} L ${sx(64)} ${sy(552)} L ${sx(64)} ${sy(565)} L ${sx(40)} ${sy(565)}`} fill="none" stroke="#52656d" strokeWidth={surround?65:12}/>
    <rect x={sx(125)} y={sy(720)} width="300" height={surround?2400:300} fill="#205568"/>
    {state?.kind==='ACCESS'&&<><rect x={sx(74)} y={sy(660)} width={6*(surround?10:1.5)} height={60*(surround?10:.36)} fill="#3f7765"/>{AccessPlanner.zones.map(z=><g key={z.id}><circle cx={sx(z.x)} cy={sy(z.z)} r={surround?8:4} fill={state.access?.zone?.id===z.id?'#80f5c0':z.stepFree?'#76b7ad':'#e69b82'}/><text x={sx(z.x)+8} y={sy(z.z)} fill="white" fontSize={surround?12:8}>{z.id}</text></g>)}</>}
    {state?.phase==='ACCESS_EXIT'&&state.access?.approved&&(()=>{const p=AccessPlanner.position(state);return <text x={sx(p.x)} y={sy(p.z)} fill="#f9df9c" fontSize={surround?24:12}>{state.access.needs.wheelchair?'♿':'●'}</text>;})()}
    {route.length>0&&<polyline points={route.map(p=>`${sx(p.x)},${sy(p.z)}`).join(' ')} fill="none" stroke="#60e5c0" strokeWidth="3"/>}
    {[[65,629,'VILLA'],[40,576,'P-07'],[8.9,610,'STOP'],[112,616,'B-12']].filter(()=>state?.kind!=='ACCESS').map(([x,z,label])=><g key={label}><circle cx={sx(x)} cy={sy(z)} r={surround?8:4} fill="#f4cb72"/><text x={sx(x)+7} y={sy(z)} fill="#e3f3ed" fontSize={surround?12:8}>{label}</text></g>)}
    <g transform={`translate(${sx(pose.x)},${sy(pose.z)}) rotate(${pose.yaw*180/Math.PI})`}><rect x={surround?-9:-4} y={surround?-20:-7} width={surround?18:8} height={surround?40:14} rx="3" fill="#a0ffdf"/><path d="M -3 -4 L 0 -9 L 3 -4" fill="white"/></g>
    {state?.kind==='PICKUP'&&!state.passenger&&<circle cx={sx(70-(state.phase==='BOARD'?Math.min(1,state.stageTime/5)*3:0))} cy={sy(628-(state.phase==='BOARD'?Math.min(1,state.stageTime/5)*2:0))} r={surround?6:3} fill="#ffce8d"/>}
    {['COURIER','HANDOFF'].includes(state?.phase)&&<circle cx={sx(state.kind==='MEAL'?67:113)} cy={sy((state.kind==='MEAL'?638:630)-(state.phase==='HANDOFF'?10:Math.min(10,state.stageTime*1.3)))} r={surround?6:3} fill="#f5a953"/>}
    {state?.hazards&&Math.floor(state.elapsed*2)%2===0&&<g fill="#ffc35a"><circle cx={sx(pose.x)-14} cy={sy(pose.z)} r="5"/><circle cx={sx(pose.x)+14} cy={sy(pose.z)} r="5"/></g>}
    {surround&&<><text x="125" y="22" fill="white">NORTH ↑</text><text x="130" y="308" fill="white">SOUTH</text><text x="6" y="160" fill="white">WEST</text><text x="270" y="160" fill="white">EAST</text><circle cx="160" cy="160" r="90" fill="none" stroke="#a0ffdf" strokeDasharray="5 5"/></>}
  </svg>;
}
function MissionViewport({ state, playing, cameraOffset = 0, compact = false }) {
 const canvas=React.useRef(null),latest=React.useRef(state);latest.current=state;
 React.useEffect(()=>{
  const el=canvas.current,c=el.getContext('2d');if(!c)return;
  const scenery=SceneryAssets.get();
  let w=1,h=1,raf,oldKey='',camera={x:-1.75,z:0,yaw:0};
  const resize=()=>{const b=el.getBoundingClientRect();w=Math.max(compact?160:320,b.width);h=Math.max(compact?140:300,b.height);const d=Math.min(devicePixelRatio||1,2);el.width=w*d;el.height=h*d;c.setTransform(d,0,0,d,0,0);oldKey='';};
  const observer=new ResizeObserver(resize);observer.observe(el);resize();
  const buildings=[];
  for(let z=35;z<530;z+=45)for(const side of [-1,1])buildings.push({x:side===1?14:-28,z,w:14,d:30,h:12+(Math.floor(z/45)%4)*4,color:side===1?'#b4b4a6':'#899ba2'});
  buildings.push({accessOnly:true,x:80,z:602,w:18,d:60,h:9,color:'#b6c5c1',label:'문화센터 · 무단차 입구'}, {residentialOnly:true,x:71,z:616,w:11,d:15,h:10,color:'#c9baa7',label:'리버빌라 101'}, {residentialOnly:true,x:73,z:653,w:18,d:12,h:13,color:'#b6aea0'},{x:23,z:585,w:25,d:11,h:12,color:'#a6b3b2',label:'CENTRAL MALL'});
  const draw=()=>{
   raf=requestAnimationFrame(draw);const s=latest.current;
   const pose=s?.pose||{x:-1.75,z:0,yaw:0};
   const accessPerson=s?.phase==='ACCESS_EXIT'&&s.access?.approved&&!compact?AccessPlanner.position(s):null;
   const goal={...pose,yaw:accessPerson?Math.atan2(accessPerson.x-pose.x,accessPerson.z-pose.z):pose.yaw+cameraOffset+(s?.phase==='BOARD' && !compact ? .5 : 0)};
   if(Math.hypot(goal.x-camera.x,goal.z-camera.z)>80)camera={...goal};
   camera.x+=(goal.x-camera.x)*.3;camera.z+=(goal.z-camera.z)*.3;
   const delta=Math.atan2(Math.sin(goal.yaw-camera.yaw),Math.cos(goal.yaw-camera.yaw));camera.yaw+=delta*.15;
   const key=[w,h,camera.x.toFixed(3),camera.z.toFixed(3),camera.yaw.toFixed(4),s?.phase,s?.stageTime?.toFixed(2),s?.passenger].join('/');if(key===oldKey)return;oldKey=key;
   const horizon=h*.39,focal=w*.73,cos=Math.cos(camera.yaw),sin=Math.sin(camera.yaw);
   const local=([x,y,z])=>[(x-camera.x)*cos-(z-camera.z)*sin,y,(x-camera.x)*sin+(z-camera.z)*cos];
   const project=([x,y,z])=>[w/2+x*focal/z,horizon+(1.45-y)*focal/z];
   function poly(points,color){
    let a=points.map(local),out=[];
    for(let i=0;i<a.length;i++){const p=a[i],q=a[(i+1)%a.length],inside=p[2]>=.8,next=q[2]>=.8;if(inside)out.push(p);if(inside!==next){const t=(.8-p[2])/(q[2]-p[2]);out.push(p.map((v,k)=>v+(q[k]-v)*t));}}
    if(out.length<3)return;c.beginPath();out.map(project).forEach(([x,y],i)=>i?c.lineTo(x,y):c.moveTo(x,y));c.closePath();c.fillStyle=color;c.fill();
   }
   // Affine triangle subdivision maps cached material textures into perspective.
   function texture(points,image,opacity=.45,divisions=2){
    const coords=points.map(local);if(coords.some(p=>p[2]<1.5)||coords.every(p=>p[2]>130))return;
    const grid=(u,v)=>{const a=points[0].map((x,k)=>x+(points[1][k]-x)*u),b=points[3].map((x,k)=>x+(points[2][k]-x)*u);return project(local(a.map((x,k)=>x+(b[k]-x)*v)));};
    function triangle(uv,xy){
     const [a,b,d]=uv,[p,q,r]=xy,ux=b[0]-a[0],uy=b[1]-a[1],vx=d[0]-a[0],vy=d[1]-a[1],det=ux*vy-vx*uy;if(Math.abs(det)<.001)return;
     const aa=((q[0]-p[0])*vy-(r[0]-p[0])*uy)/det,bb=((q[1]-p[1])*vy-(r[1]-p[1])*uy)/det,cc=(ux*(r[0]-p[0])-vx*(q[0]-p[0]))/det,dd=(ux*(r[1]-p[1])-vx*(q[1]-p[1]))/det;
     c.save();c.globalAlpha=opacity;c.beginPath();c.moveTo(...p);c.lineTo(...q);c.lineTo(...r);c.closePath();c.clip();c.transform(aa,bb,cc,dd,p[0]-aa*a[0]-cc*a[1],p[1]-bb*a[0]-dd*a[1]);c.drawImage(image,0,0);c.restore();
    }
    const n=compact?1:divisions;
    for(let y=0;y<n;y++)for(let x=0;x<n;x++){
     const uv=[[x/n,y/n],[(x+1)/n,y/n],[(x+1)/n,(y+1)/n],[x/n,(y+1)/n]],xy=uv.map(p=>grid(...p)),src=uv.map(([u,v])=>[u*image.width,v*image.height]);
     triangle([src[0],src[1],src[2]],[xy[0],xy[1],xy[2]]);triangle([src[0],src[2],src[3]],[xy[0],xy[2],xy[3]]);
    }
   }
   const rect=(x,z,width,depth,y,color)=>poly([[x,y,z],[x+width,y,z],[x+width,y,z+depth],[x,y,z+depth]],color);
   const label=(x,y,z,text,color='#123d36')=>{const p=local([x,y,z]);if(p[2]<3||p[2]>150)return;const [px,py]=project(p),size=Math.max(10,Math.min(25,300/p[2]));c.font=`600 ${size}px sans-serif`;const tw=c.measureText(text).width;c.fillStyle=color;c.fillRect(px-tw/2-8,py-size-5,tw+16,size+12);c.fillStyle='white';c.fillText(text,px-tw/2,py);};
   const sky=c.createLinearGradient(0,0,0,horizon);sky.addColorStop(0,'#6a93ab');sky.addColorStop(1,'#dde7e5');c.fillStyle=sky;c.fillRect(0,0,w,h);c.fillStyle='#6e856c';c.fillRect(0,horizon,w,h-horizon);
   // Soft atmospheric skyline and high clouds soften the flat horizon.
   c.fillStyle='#829b9c';c.beginPath();c.moveTo(0,horizon);for(let x=0;x<=w;x+=w/40)c.lineTo(x,horizon-12-9*Math.sin(x/w*18)-7*Math.sin(x/w*31));c.lineTo(w,horizon);c.fill();
   const sun=c.createRadialGradient(w*.76,h*.12,1,w*.76,h*.12,w*.23);sun.addColorStop(0,'#ffefcf50');sun.addColorStop(1,'#fff2cf00');c.fillStyle=sun;c.fillRect(0,0,w,horizon);
   for(let i=0;i<5;i++){const x=w*(.12+i*.19),y=h*(.12+.035*Math.sin(i*5)),g=c.createRadialGradient(x,y,0,x,y,w*.12);g.addColorStop(0,'#f5f3e92a');g.addColorStop(1,'#ffffff00');c.fillStyle=g;c.fillRect(x-w*.13,y-h*.06,w*.26,h*.12);}

   rect(-300,-200,600,1200,0,'#849780');rect(125,500,90,270,.01,'#397a8e');
   for(let z=505;z<770;z+=6)rect(126,z,88,.15,.02,'#71a6b2');
   rect(-10,-100,20,900,.02,'#a6aaa2');rect(-7,-100,14,900,.04,'#464f54');
   rect(7,596,111,8,.04,'#565e60');rect(61,600,8,44,.04,'#5d6261');rect(61,636,30,8,.04,'#5d6261');rect(83,600,8,44,.04,'#5d6261');
   rect(7,540,16,17,.045,'#575f63');rect(5,598,8,18,.045,'#565e60');rect(96,596,20,20,.045,'#606965');rect(7,548,62,9,.05,'#575f63');rect(22,542,51,45,.025,'#697171');rect(108,600,8,40,.04,'#606965');rect(117,500,5,270,.04,'#a6af9f');
   // Accessible side bays and a continuous step-free entrance path.
   rect(66,552,8,108,.05,'#606c6e');rect(74,602,6,58,.065,'#789b85');
   for(const z of [610,648]){rect(66,z-6,8,12,.07,'#3b746d');rect(72,z-4,2,8,.075,'#789d83');for(let dz=-4;dz<5;dz+=1)rect(72,z+dz,2,.09,.08,'#d6e9cf');}
   rect(74,608,6,4,.08,'#a3b5a0');label(78,2,612,'무단차 입구 · 문화센터');
   rect(7.2,590,4.2,30,.06,s?.hazards?'#8a7944':'#526b5e');rect(110,610,7,16,.06,'#3e7b68');
   // World-anchored asphalt, repairs, curb stones and footpath joints.
   const roadTexture=(x,z,bw,bd)=>texture([[x,.065,z],[x+bw,.065,z],[x+bw,.065,z+bd],[x,.065,z+bd]],scenery.asphalt,.72,2);
   for(let z=Math.max(-24,Math.floor((camera.z-100)/12)*12);z<Math.min(800,camera.z+130);z+=12)for(const x of [-7,0])roadTexture(x,z,7,12);
   for(const [x,z,bw,bd] of [[7,596,111,8],[61,600,8,44],[61,636,30,8],[83,600,8,44],[7,548,62,9],[22,542,51,45],[108,600,8,40]])roadTexture(x,z,bw,bd);
   for(let z=Math.max(0,Math.floor((camera.z-90)/2)*2);z<Math.min(760,camera.z+110);z+=2){
    for(const side of [-1,1]){const x=side===1?7:-10;rect(x,z,3,.025,.07,'#6e777258');rect(side===1?7: -7.18,z,.18,1.95,.1,'#c9c9b8');}
   }
   // Fine aggregate remains visible near the bonnet, anchored to world coordinates.
   if(Math.abs(Math.sin(camera.yaw))<.7)for(let z=Math.floor(camera.z-30);z<camera.z+35;z+=.8)for(let x=-6.8;x<7;x+=.85){
    const hash=Math.sin(x*47+z*17)*.5+.5,xx=x+(hash-.5)*.5,zz=z+hash*.5,q=local([xx,0,zz]);
    if(q[2]>2&&q[2]<32)rect(xx,zz,.015+hash*.025,.025,.075,hash>.5?'#d2d5c326':'#15283028');
   }
   for(const [x,z] of [[-4,115],[1,284],[4,410]]){rect(x,z,1.2,2,.07,'#394449');rect(x+.04,z+.04,1.1,1.9,.071,'#444d50');}
   for(let z=58;z<700;z+=90)for(const side of [-1,1])poly([[side*9,.075,z],[side*9+3,.075,z+2],[side*9-1,.075,z+9],[side*9-3,.075,z+7]],'#223c3020');
   for(let x=-6.6;x<7;x+=1.1)rect(x,561,.6,3,.08,'#dedfd2');
   for(let x=-3.5;x<=3.5;x+=3.5)for(let z=0;z<780;z+=12)rect(x-.05,z,.1,5,.055,'#e6e7d9');
   rect(-6.98,-50,.1,800,.055,'#d9ca90');rect(6.9,-50,.1,800,.055,'#d9ca90');
   for(let x=26;x<65;x+=3.5){rect(x,570,.08,10,.06,'#e3e4da');rect(x,580,3.4,.08,.06,'#e3e4da');}
   rect(38.4,570,3.3,10,.065,'#437c6d');label(40,1,581,'P-07 · 예약 주차면');label(10,2,610,'단기 정차 · 1분');label(114,2,625,'B-12 · 한강 인계 베이');
   // Route is drawn from the very same world-space trajectory used by the engine.
   if(s){const route=(s.routeOverrides||MissionEngine.routes[s.kind])[s.stage];if(route)for(let i=0;i<route.samples.length;i+=5){const p=route.samples[i];if(Math.hypot(p.x-camera.x,p.z-camera.z)>3)rect(p.x-.15,p.z,.3,.6,.08,'#52c7ae');}}
   const objects=[];
   const face=(points,color,material=null)=>{const distance=points.reduce((n,p)=>n+local(p)[2],0)/points.length;if(distance>.8&&distance<400)objects.push({points,color,distance,material});};
   function box(x,z,bw,bd,bh,color,material=null){
    face([[x,0,z],[x+bw,0,z],[x+bw,bh,z],[x,bh,z]],color,material);
    face([[x+bw,0,z+bd],[x,0,z+bd],[x,bh,z+bd],[x+bw,bh,z+bd]],color,material);
    face([[x,0,z+bd],[x,0,z],[x,bh,z],[x,bh,z+bd]],'#7b8e94',material);
    face([[x+bw,0,z],[x+bw,0,z+bd],[x+bw,bh,z+bd],[x+bw,bh,z]],'#a3aba9',material);
    face([[x,bh,z],[x+bw,bh,z],[x+bw,bh,z+bd],[x,bh,z+bd]],'#bdc5bd');
   }
   const visibleBuildings=buildings.filter(b=>s?.kind==='ACCESS'?!b.residentialOnly:!b.accessOnly);
   visibleBuildings.forEach(b=>{
    box(b.x,b.z,b.w,b.d,b.h,b.color,b.label?.includes('빌라')?scenery.brick:scenery.concrete);
    // Roof coping, shop awnings and exterior balcony ledges.
    face([[b.x-.2,b.h,b.z-.2],[b.x+b.w+.2,b.h,b.z-.2],[b.x+b.w+.2,b.h+.25,b.z-.2],[b.x-.2,b.h+.25,b.z-.2]],'#d4d3c6');
    for(let y=2;y<b.h-1;y+=3){
     for(let x=b.x+1;x<b.x+b.w-1;x+=3)for(const z of [b.z-.03,b.z+b.d+.03])face([[x,y,z],[x+1.6,y,z],[x+1.6,y+1.8,z],[x,y+1.8,z]],'#456473');
     for(let z=b.z+1;z<b.z+b.d-2;z+=3.3)for(const x of [b.x-.03,b.x+b.w+.03]){
      face([[x,y,z],[x,y,z+1.8],[x,y+1.8,z+1.8],[x,y+1.8,z]],'#496979');
      face([[x,y+1.45,z],[x,y+1.45,z+1.8],[x,y+1.7,z+1.8],[x,y+1.7,z]],'#8daab2');
     }
    }
    const streetX=b.x>0?b.x-.06:b.x+b.w+.06;
    for(let y=3.5;y<b.h;y+=3){face([[streetX,y,b.z+.5],[streetX,y,b.z+b.d-.5],[streetX+(b.x>0?-.32:.32),y-.15,b.z+b.d-.5],[streetX+(b.x>0?-.32:.32),y-.15,b.z+.5]],'#bfc2b8');}
    for(let z=b.z+2;z<b.z+b.d-2;z+=5.5){face([[streetX,.2,z],[streetX,.2,z+3.8],[streetX,2.2,z+3.8],[streetX,2.2,z]],'#274550');face([[streetX,2.3,z],[streetX,2.3,z+4],[streetX+(b.x>0?-.7:.7),2,z+4],[streetX+(b.x>0?-.7:.7),2,z]],Math.floor(b.z/45)%2?'#657f73':'#b29973');}

    face([[streetX,.2,b.z+1],[streetX,.2,b.z+b.d-1],[streetX,2.1,b.z+b.d-1],[streetX,2.1,b.z+1]],'#385865');
   });
   // Lamp columns, railings and riverside seating anchor the scene at human scale.
   for(let z=20;z<700;z+=35)for(const x of [-8.5,8.5]){
    const q=local([x,0,z]);if(q[2]>2&&q[2]<180)objects.push({distance:q[2],draw(){const a=project(q),b=project(local([x,5.5,z]));c.strokeStyle='#60747b';c.lineWidth=Math.max(1,3*focal/q[2]/35);c.beginPath();c.moveTo(...a);c.lineTo(...b);c.lineTo(b[0]-Math.sign(x)*12,b[1]);c.stroke();}});
   }
   for(let z=520;z<730;z+=12){
    box(122,z,.12,.12,1,'#b5bfb4');face([[122,.8,z],[122,.8,z+12],[122,.9,z+12],[122,.9,z]],'#cbd4c9');
    if(z%24===16){box(118,z,1.2,2,.5,'#9f9174');box(118,z+1.8,1.2,.15,1,'#a99b80');}
   }
   for(let z=25;z<720;z+=30)for(const x of [-9.2,9.2,120]){
    if(x===120&&z<530)continue;
    const q=local([x,0,z]);if(q[2]<3||q[2]>240)continue;
    objects.push({distance:q[2],draw(){
     const foot=project(q),height=5.5*focal/q[2],width=height*.75;
     c.fillStyle='#173d2920';c.beginPath();c.ellipse(foot[0]+width*.25,foot[1],width*.52,height*.055,0,0,Math.PI*2);c.fill();
     c.drawImage(scenery.trees[Math.abs(Math.floor(z/30))%3],foot[0]-width/2,foot[1]-height,width,height);
    }});
   }
   // Parked cars occupy other bays; P-07 remains free for the reversing vehicle.
   for(const x of [28,32,47,54,61]){box(x,573,1.8,4.3,1.1,'#a7b9bd');box(x+.15,574,1.5,2.1,1.55,'#657f8c');}
   // Main-road traffic, clear of the manoeuvring path.
   for(const [x,z] of [[-5.25,80],[-1.75,150],[1.75,240],[-5.25,440]]){const zz=z+(s?.elapsed||0)*8;if(zz<760){box(x-.9,zz,1.8,4,1.1,'#8299a4');box(x-.7,zz+1,1.4,2,1.6,'#465e6a');}}
   // Parking gate with lifting barrier when the car approaches.
   box(18,546,.5,.5,1,'#c3a561');const raised=s&&s.phase==='GATE'&&s.travel>18;face([[18,1,546],[18,1.18,546],[18,raised?4:1.18,553],[18,raised?4:1,553]],'#e6e2cd');
   objects.sort((a,b)=>b.distance-a.distance).forEach(o=>{
    if(o.draw)o.draw();else{poly(o.points,o.color);if(o.material)texture(o.points,o.material,.46,3);if(o.distance>120)poly(o.points,`rgba(192,210,210,${Math.min(.36,(o.distance-120)/650)})`);}
   });
   visibleBuildings.forEach(b=>{if(b.label)label(b.x+b.w/2,3,b.z-.1,b.label);});
   // Walking person and courier are independent of the vehicle pose.
   const person=(x,z,text,color,walking=true,heightMeters=1.78)=>{
    const q=local([x,0,z]);if(q[2]<1||q[2]>110)return;const foot=project(q),height=heightMeters*focal/q[2],width=height*110/240;
    const phase=walking?Math.floor((s?.elapsed||0)*8)%8:0;
    c.fillStyle='#172e292c';c.beginPath();c.ellipse(foot[0],foot[1],width*.45,height*.035,0,0,Math.PI*2);c.fill();
    c.drawImage(scenery.person(color,phase),foot[0]-width/2,foot[1]-height,width,height);
    if(text)label(x,2.1,z,text);
   };
   // Background pedestrians stay on pavements and the riverside promenade.
   for(const [i,x,z] of [[0,-8.1,78],[1,8.3,166],[2,-8.4,310],[3,8.5,460],[4,119,570],[5,119,658]]){
    const walk=Math.sin((s?.elapsed||0)*.12+i)*3;person(x,z+walk,'',['#7d6b59','#58758b','#6a7660'][i%3],Boolean(s&&!s.done&&!s.waiting));
   }
   if(s?.kind==='PICKUP'&&!s.passenger){const t=s.phase==='BOARD'?Math.min(1,s.stageTime/5):0;person(70-t*3,628-t*2,'탑승 예약자','#4a7695');}
   if(['MEAL','RIVER'].includes(s?.kind)&&['COURIER','HANDOFF'].includes(s?.phase))person(s.kind==='MEAL'?67:113,(s.kind==='MEAL'?638:630)-(s.phase==='HANDOFF'?10:Math.min(10,s.stageTime*1.3)),s.received?'인계 완료':'배달원 · 주문 024','#d28a45',s.phase==='COURIER');
   if(s?.kind==='ACCESS'&&s.access?.zone){
    const a=s.access,z=a.zone;
    if(a.approved){
     rect(z.x+.9,z.z-.5,2.8,1,.09,'#a1baa9');
     const p=AccessPlanner.position(s);person(p.x,p.z,'입구까지 무단차 이동','#586c91',!a.needs.wheelchair,a.needs.wheelchair?1.3:1.78);
     if(a.needs.wheelchair){const q=local([p.x,0,p.z]);if(q[2]>1){const foot=project(q),scale=focal/q[2];c.strokeStyle='#253f4c';c.lineWidth=Math.max(1,.035*scale);for(const dx of [-.22,.22]){c.beginPath();c.ellipse(foot[0]+dx*scale,foot[1]-.24*scale,.19*scale,.24*scale,0,0,Math.PI*2);c.stroke();}c.strokeStyle='#a9c6c8';c.beginPath();c.moveTo(foot[0]-.23*scale,foot[1]-.55*scale);c.lineTo(foot[0]+.23*scale,foot[1]-.55*scale);c.lineTo(foot[0]+.35*scale,foot[1]-.1*scale);c.stroke();}}
    }
    if(a.needs.staff&&a.staffAccepted)person(z.x+2,z.z+4,'지원 직원','#6a8c72',false);
   }
   // Dashboard and A-pillars stay fixed while the entire world rotates on turns.
   c.fillStyle='#1e2b32';c.beginPath();c.moveTo(0,0);c.lineTo(w*.03,0);c.lineTo(w*.1,h*.9);c.lineTo(0,h);c.fill();c.beginPath();c.moveTo(w,0);c.lineTo(w*.97,0);c.lineTo(w*.9,h*.9);c.lineTo(w,h);c.fill();
   const dash=c.createLinearGradient(0,h*.85,0,h);dash.addColorStop(0,'#37494f');dash.addColorStop(1,'#0b1720');c.fillStyle=dash;c.beginPath();c.moveTo(0,h);c.lineTo(0,h*.93);c.quadraticCurveTo(w/2,h*.79,w,h*.93);c.lineTo(w,h);c.fill();
   c.fillStyle='#d0f3ec';c.font=`500 ${Math.max(17,w*.019)}px sans-serif`;c.textAlign='center';c.fillText(`${s?.gear||'P'}    ${Math.round(s?.speedKph||0)} km/h`,w/2,h*.96);c.textAlign='left';
   if(s?.hazards&&Math.floor((s.elapsed||0)*2)%2===0){c.fillStyle='#ffc15a';c.font='bold 28px sans-serif';c.fillText('◀',w*.43,h*.96);c.fillText('▶',w*.56,h*.96);}
  };draw();return()=>{cancelAnimationFrame(raf);observer.disconnect();};
 },[]);
 const title=state?(state.phase==='ACCESS_SETUP'?'필요한 승하차 지원 · 하차 위치 비교':state.phase==='MEAL_SETUP'?'차량 내부 디스플레이 · 집까지 식사 계획':state.done?({ACCESS:'무단차 하차 · 문화센터 입구 도착',PICKUP:'승객 탑승 · 본선 복귀 완료',PARKING:'P-07 주차면 · 주차 완료',SHORT_STOP:'비상등 해제 · 본선 재출발',MEAL:'식사 주문 · 음식 인계 완료',RIVER:'한강 위치 확인 · 음식 인계 완료'}[state.kind]):MissionEngine.presets[state.kind].stages[state.stage][1]):'목적에 따라 달라지는 실제 이동 경로';
 if(compact)return <div className="camera-scene"><canvas ref={canvas} aria-label="모의 차량 카메라 시야"/></div>;
 return <div className="driver-scene"><canvas ref={canvas} aria-label="월드 좌표 기반 운전자 시점 시뮬레이션"/><div className="driver-view-label">CURBLINK JOURNEY<span>가상 도로 · 운전자 시점</span></div><div className="driver-navigation"><span className="driver-turn">{state?.done?'✓':state?.gear==='R'?'↶':state?.phase==='MAIN'?'↑':'↗'}</span><div><strong>{title}</strong><small>{state?.zone.label||'픽업 · 주차 · 잠시 정차 · 식사 주문 · 한강 인계'}</small></div></div><div className="driver-minimap"><div>WORLD ROUTE <span>N ↑</span></div><MissionMap state={state}/></div>{state?.phase==='MAIN'&&<div className="driver-lanes">{[1,2,3,4].map(l=><span key={l} className={(state.pose.z>320?4:2)===l?'recommended':''}>{['↰','↑','↑','↱'][l-1]}<small>{l}차선</small></span>)}</div>}<div className="driver-caption">{state?.kind==='ACCESS'&&state.phase==='ACCESS_EXIT'&&!state.done?'우측 하차 동선 확인 · 차량 P단 정차 유지':state?.passenger?'● 승객 탑승 완료':state?.hazards?'◀ ▶ 비상등 점멸 · 잠시 정차 중':state?.done?'시나리오 완료':state?.waiting?'정차 중 · 아래에서 확인해 주세요':playing?'주행 경로와 차량 시야가 함께 이동합니다':'재생 대기'}</div></div>;
}
