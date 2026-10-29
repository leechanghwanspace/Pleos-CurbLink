package io.curblink.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clipToBounds
import kotlin.math.*

/** All geometry is local fixture data. This is a synthetic world, never a camera feed. */
@Composable fun JourneyViewport(s: MobilityState, view: (Int) -> Unit) {
    val run = s.journey ?: return
    val displaySpeed = if (s.paused || !s.vehicleValid) 0 else run.speedKph.toInt()
    val mint = Color(0xFF85E5C7)
    Column(Modifier.fillMaxWidth().background(Color(0xFF10212D), RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(run.scenario.label, color = mint, fontSize = 22.sp)
        Text("${run.gear} · $displaySpeed km/h · ${if(s.paused) "일시정지 · " else ""}${run.current.label}", color = Color.White, fontSize = 18.sp)
        Text("모의 공간 · 실제 카메라·지도·차량 미연결", color = Color(0xFFA6BCC9), fontSize = 14.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("운전자 전방", "우측", "후방", "좌측", "경로 전체").forEachIndexed { i, label ->
                FilterChip(s.sceneView == i, { view(i) }, label = { Text(label, fontSize = 16.sp) })
            }
        }
        Canvas(Modifier.fillMaxWidth().height(340.dp).clipToBounds().semantics {
            contentDescription = "모의 ${listOf("전방", "우측", "후방", "좌측", "전체 경로")[s.sceneView]} 시야. ${run.current.label}. 기어 ${run.gear}. ${if (run.hazards) "비상등 점멸" else "비상등 꺼짐"}. ${if (run.passenger) "승객 탑승 완료" else "승객 탑승 전"}"
        }) {
            val w = size.width; val h = size.height
            val pose = run.pose
            val cameraYaw = pose.second + when (s.sceneView) { 1 -> PI / 2; 2 -> PI; 3 -> -PI / 2; else -> 0.0 }
            val overhead = s.sceneView == 4
            val scale = min(w / 170f, h / 735f)
            fun project(p: WorldPoint, height: Double = 0.0): Offset? {
                if (overhead) return Offset(w / 2 + ((p.x - 35) * scale).toFloat(), h - ((p.z + 10) * scale).toFloat())
                val dx = p.x - pose.first.x; val dz = p.z - pose.first.z
                val right = dx * cos(cameraYaw) - dz * sin(cameraYaw)
                val depth = dx * sin(cameraYaw) + dz * cos(cameraYaw) + 7
                if (depth < 1.2) return null
                val focal = w * .72
                return Offset((w / 2 + right * focal / depth).toFloat(), (h * .4 + (2.8 - height) * focal / depth).toFloat())
            }
            fun polygon(points: List<WorldPoint>, color: Color, height: Double = 0.0) {
                val projected = points.map { project(it, height) ?: return }
                val path = Path().apply { projected.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }
                drawPath(path, color)
            }
            drawRect(Color(0xFF142735))
            if (!overhead) {
                drawRect(Color(0xFF29434A), topLeft = Offset(0f, h * .4f), size = Size(w, h * .6f))
                drawCircle(Color(0xFFACC9CA), 25f, Offset(w * .82f, h * .15f))
            }
            // Short quads are clipped independently as the camera travels and turns.
            for (z in 0..710 step 5) {
                polygon(listOf(WorldPoint(-7.0,z.toDouble()),WorldPoint(7.0,z.toDouble()),WorldPoint(7.0,z+5.0),WorldPoint(-7.0,z+5.0)), Color(0xFF41545D))
                if (z % 10 == 0) for (x in listOf(-3.5,0.0,3.5)) {
                    val a = project(WorldPoint(x,z.toDouble())); val b = project(WorldPoint(x,z+3.0))
                    if (a != null && b != null) drawLine(Color(0xFFB9C8C8), a,b,if(overhead) 1f else 2f)
                }
            }
            // Connected alley, parking entrance, aisle and river access.
            val roads = listOf(listOf(WorldPoint(5.25,600.0),WorldPoint(112.0,600.0)),
                listOf(WorldPoint(65.0,600.0),WorldPoint(65.0,640.0),WorldPoint(87.0,640.0),WorldPoint(87.0,600.0)),
                listOf(WorldPoint(5.25,552.0),WorldPoint(70.0,552.0),WorldPoint(70.0,650.0)),
                listOf(WorldPoint(40.0,565.0),WorldPoint(64.0,565.0)))
            for (road in roads) for ((a,b) in road.zipWithNext()) {
                val len = hypot(b.x-a.x,b.z-a.z); val steps = ceil(len / 4).toInt().coerceAtLeast(1)
                val nx = -(b.z-a.z)/len*3; val nz = (b.x-a.x)/len*3
                for(i in 0 until steps) {
                    val u = i.toDouble()/steps; val v=(i+1.0)/steps
                    val p=WorldPoint(a.x+(b.x-a.x)*u,a.z+(b.z-a.z)*u); val q=WorldPoint(a.x+(b.x-a.x)*v,a.z+(b.z-a.z)*v)
                    polygon(listOf(WorldPoint(p.x+nx,p.z+nz),WorldPoint(q.x+nx,q.z+nz),WorldPoint(q.x-nx,q.z-nz),WorldPoint(p.x-nx,p.z-nz)),Color(0xFF50616A))
                }
            }
            polygon(listOf(WorldPoint(118.0,540.0),WorldPoint(150.0,540.0),WorldPoint(150.0,710.0),WorldPoint(118.0,710.0)),Color(0xFF285D74))
            polygon(listOf(WorldPoint(74.0,608.0),WorldPoint(82.0,608.0),WorldPoint(82.0,650.0),WorldPoint(74.0,650.0)),Color(0xFF53826B))
            val buildings = buildList {
                for(z in 20..510 step 35) { add(WorldPoint(-20.0,z.toDouble())); add(WorldPoint(22.0,z.toDouble())) }
                addAll(listOf(WorldPoint(50.0,615.0),WorldPoint(96.0,629.0),WorldPoint(88.0,610.0)))
            }.sortedByDescending { hypot(it.x-pose.first.x,it.z-pose.first.z) }
            for (b in buildings) {
                val ground=listOf(WorldPoint(b.x-6,b.z-6),WorldPoint(b.x+6,b.z-6),WorldPoint(b.x+6,b.z+6),WorldPoint(b.x-6,b.z+6))
                polygon(ground,Color(0xFF263E4B),8.0)
                if(!overhead) for ((a,c) in (ground+ground.first()).zipWithNext()) {
                    val p=project(a) ?: continue; val q=project(c) ?: continue; val r=project(c,8.0) ?: continue; val t=project(a,8.0) ?: continue
                    drawPath(Path().apply { moveTo(p.x,p.y);lineTo(q.x,q.y);lineTo(r.x,r.y);lineTo(t.x,t.y);close() },Color(0xFF375666))
                }
            }
            // P-07 parking rectangle is oriented along the reverse segment.
            val bay = listOf(WorldPoint(37.5,571.0),WorldPoint(42.5,571.0),WorldPoint(42.5,579.0),WorldPoint(37.5,579.0))
            val bayProjected=bay.map { project(it) }
            if(bayProjected.all { it!=null }) drawPath(Path().apply { bayProjected.forEachIndexed { i,p -> if(i==0)moveTo(p!!.x,p.y) else lineTo(p!!.x,p.y) };close() },Color(0xFFF1CA85),style=Stroke(2f))
            for(stage in run.scenario.stages) for((a,b) in stage.points.zipWithNext()) {
                val len=hypot(b.x-a.x,b.z-a.z);val steps=ceil(len/3).toInt().coerceAtLeast(1)
                for(i in 0 until steps) {
                    val p=project(WorldPoint(a.x+(b.x-a.x)*i/steps,a.z+(b.z-a.z)*i/steps),.05)
                    val q=project(WorldPoint(a.x+(b.x-a.x)*(i+1)/steps,a.z+(b.z-a.z)*(i+1)/steps),.05)
                    if(p!=null&&q!=null)drawLine(mint.copy(alpha=.75f),p,q,if(overhead)3f else 5f,StrokeCap.Round)
                }
            }
            val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.WHITE;textSize=15.dp.toPx() }
            for((label,p) in listOf("B-12" to WorldPoint(112.0,616.0),"빌라 101" to WorldPoint(58.0,625.0),"P-07" to WorldPoint(40.0,576.0),"무단차 입구" to WorldPoint(80.0,610.0))) {
                if(!overhead && hypot(p.x-pose.first.x,p.z-pose.first.z)>90) continue
                val screen=project(p,3.0) ?: continue
                if(screen.x in 0f..w && screen.y in 0f..h) drawContext.canvas.nativeCanvas.drawText(label,screen.x,screen.y,paint)
            }
            var actor:WorldPoint?=null
            if(run.current.id=="BOARD"&&!run.passenger)actor=WorldPoint(58.0+7*run.fraction,625.0)
            if(run.current.id=="COURIER"||run.current.id=="HANDOFF")actor=WorldPoint(pose.first.x+8*(1-run.fraction),pose.first.z+2)
            if(run.current.id=="ACCESS_EXIT") {
                val z=if(s.bay?.id=="A3")648.0 else 610.0
                val f=run.fraction
                actor=if(f<.5)WorldPoint(71+5*f*2,z) else WorldPoint(76+4*(f-.5)*2,z+(610-z)*(f-.5)*2)
            }
            actor?.let { p -> project(p,1.0)?.let { drawCircle(Color(0xFFF1CA85),8.dp.toPx(),it);drawLine(Color.White,it,Offset(it.x,it.y+15),3f) } }
            if(overhead) {
                project(pose.first)?.let { drawCircle(mint,6.dp.toPx(),it) }
            } else {
                drawRoundRect(Color(0xFF0B141E),topLeft=Offset(w*.12f,h*.88f),size=Size(w*.76f,h*.12f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(20f))
                drawContext.canvas.nativeCanvas.drawText("${run.gear}    $displaySpeed km/h    모의 운전자 시야",w*.18f,h*.96f,paint)
            }
            if(run.current.id in setOf("REVERSE","PARKED")) {
                val side=min(180.dp.toPx(),w*.4f);val left=w-side-10;val top=10f
                drawRect(Color(0xFF0B141E),Offset(left,top),Size(side,side))
                fun inset(p:WorldPoint)=Offset(left+side/2+((p.x-40)*side/22).toFloat(),top+side*.6f-((p.z-570)*side/22).toFloat())
                val outline=bay.map(::inset)
                drawPath(Path().apply{outline.forEachIndexed{i,p->if(i==0)moveTo(p.x,p.y)else lineTo(p.x,p.y)};close()},Color(0xFFF1CA85),style=Stroke(3f))
                val car=listOf(-.9 to -2.2,.9 to -2.2,.9 to 2.2,-.9 to 2.2).map{(x,z)->inset(WorldPoint(pose.first.x+x*cos(pose.second)+z*sin(pose.second),pose.first.z-x*sin(pose.second)+z*cos(pose.second)))}
                drawPath(Path().apply{car.forEachIndexed{i,p->if(i==0)moveTo(p.x,p.y)else lineTo(p.x,p.y)};close()},mint)
                drawContext.canvas.nativeCanvas.drawText("P-07 · ${run.gear}",left+8,top+24,paint)
            }
            if(run.hazards&&s.elapsed%2==0) {
                drawCircle(Color(0xFFFFC26A),12.dp.toPx(),Offset(w*.1f,h*.9f));drawCircle(Color(0xFFFFC26A),12.dp.toPx(),Offset(w*.9f,h*.9f))
            }
        }
        Text(if(run.done) "시나리오 완료 · 홈에서 다음 여정을 시작하세요" else "${run.stage+1}/${run.scenario.stages.size} · ${run.current.label}${if(run.passenger) " · 승객 탑승 완료" else ""}${if(run.hazards) " · 비상등 점멸" else ""}",color=mint,fontSize=16.sp)
    }
}
