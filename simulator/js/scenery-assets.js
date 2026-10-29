/* Shared, deterministic scenery assets. Generated once; no per-frame random noise. */
(function(root){
 let assets;
 function get(){
  if(assets)return assets;
  let seed=17421;const random=()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296;};
  const make=(w,h,paint)=>{const canvas=document.createElement('canvas');canvas.width=w;canvas.height=h;paint(canvas.getContext('2d'),w,h);return canvas;};
  const asphalt=make(256,256,(c,w,h)=>{
   c.fillStyle='#4c5357';c.fillRect(0,0,w,h);
   for(let i=0;i<14000;i++){const v=55+random()*65;c.fillStyle=`rgba(${v},${v+3},${v+4},${.05+random()*.2})`;const size=.3+random()*1.4;c.fillRect(random()*w,random()*h,size,size);}
   c.strokeStyle='#343e4325';c.lineWidth=14;for(const x of [70,180]){c.beginPath();c.moveTo(x,0);c.bezierCurveTo(x+5,80,x-6,170,x,256);c.stroke();}
  });
  const brick=make(256,256,(c,w,h)=>{
   c.fillStyle='#a59887';c.fillRect(0,0,w,h);
   for(let row=0;row<16;row++)for(let col=-1;col<8;col++){
    const light=130+Math.floor(random()*35);c.fillStyle=`rgb(${light+16},${light+5},${light-9})`;c.fillRect(col*38+(row%2)*19,row*16,36,14);
   }
   for(let i=0;i<3500;i++){c.fillStyle=random()>.5?'#ffffff0b':'#0000000a';c.fillRect(random()*w,random()*h,random()*3,1);}
  });
  const concrete=make(256,256,(c,w,h)=>{
   c.fillStyle='#b2b2a7';c.fillRect(0,0,w,h);for(let i=0;i<13000;i++){c.fillStyle=random()>.5?'#ffffff0d':'#0000000b';c.fillRect(random()*w,random()*h,1,1);}
   c.strokeStyle='#7b85844d';c.lineWidth=1;c.strokeRect(.5,.5,127,127);c.strokeRect(128.5,128.5,127,127);
  });
  const trees=[0,1,2].map(variant=>make(360,480,(c)=>{
   c.lineCap='round';c.strokeStyle='#5f5844';c.lineWidth=15;c.beginPath();c.moveTo(180,470);c.bezierCurveTo(176,360,191,300,176,205);c.stroke();
   for(let branch=0;branch<14;branch++){const angle=-Math.PI+random()*Math.PI, endX=180+Math.cos(angle)*115,endY=210+Math.sin(angle)*140;c.lineWidth=3+random()*4;c.beginPath();c.moveTo(180,360-branch*10);c.quadraticCurveTo(180,240,endX,endY);c.stroke();}
   // Leaf clusters form an irregular silhouette rather than separate spheres.
   for(let i=0;i<4400;i++){
    const angle=random()*Math.PI*2,radius=Math.sqrt(random()),x=180+Math.cos(angle)*radius*(125+15*Math.sin(angle*5+variant)),y=174+Math.sin(angle)*radius*(145+9*Math.cos(angle*7));
    const light=(1-radius)*12+(1-x/360)*15+random()*20;
    c.fillStyle=`hsl(${89+variant*7+random()*12},${22+random()*15}%,${22+light}%)`;c.beginPath();c.ellipse(x,y,2+random()*6,1+random()*4,random()*Math.PI,0,Math.PI*2);c.fill();
   }
  }));
  const people={};
  function person(color,phase){
   const key=color+phase;if(people[key])return people[key];
   return people[key]=make(110,240,c=>{
    const stride=Math.sin(phase*Math.PI/4)*15;
    c.lineCap='round';c.lineJoin='round';
    const limb=(points,width,fill)=>{c.strokeStyle=fill;c.lineWidth=width;c.beginPath();c.moveTo(...points[0]);points.slice(1).forEach(p=>c.lineTo(...p));c.stroke();};
    limb([[46,142],[44-stride*.4,182],[38-stride,220]],14,'#34434c');limb([[64,142],[66+stride*.4,184],[72+stride,220]],14,'#2c3b46');
    limb([[31-stride,224],[44-stride,224]],10,'#252c32');limb([[65+stride,224],[78+stride,224]],10,'#252c32');
    limb([[33,83],[23+stride*.45,121],[27+stride*.5,151]],11,color);limb([[76,84],[86-stride*.45,122],[81-stride*.5,151]],11,color);
    c.fillStyle='#cfae91';for(const x of [27+stride*.5,81-stride*.5]){c.beginPath();c.ellipse(x,153,5,7,0,0,7);c.fill();}
    const coat=c.createLinearGradient(25,0,80,0);coat.addColorStop(0,color);coat.addColorStop(1,'#334c59');c.fillStyle=coat;c.beginPath();c.moveTo(38,72);c.quadraticCurveTo(55,66,73,72);c.lineTo(78,139);c.quadraticCurveTo(55,150,31,139);c.closePath();c.fill();
    c.fillStyle='#c7a58a';c.fillRect(48,57,15,18);c.beginPath();c.ellipse(55,41,19,25,0,0,7);c.fill();c.fillStyle='#413a33';c.beginPath();c.ellipse(54,25,20,14,-.1,0,7);c.fill();c.fillRect(35,25,6,20);
    c.strokeStyle='#9d7f6a';c.lineWidth=1;c.beginPath();c.moveTo(59,40);c.lineTo(62,48);c.lineTo(57,49);c.stroke();c.fillStyle='#393a35';c.fillRect(46,38,2,2);c.fillRect(62,38,2,2);
   });
  }
  assets={asphalt,brick,concrete,trees,person};return assets;
 }
 root.SceneryAssets={get};
})(typeof globalThis!=='undefined'?globalThis:window);
