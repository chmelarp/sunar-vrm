


-- kontrola videi
SELECT DISTINCT dataset, video, camera FROM sunar.videos_all
WHERE (dataset, video, camera) NOT IN (SELECT DISTINCT dataset, video, camera FROM sunar.videos)
ORDER BY dataset, video, camera



-- tohle udela par anotace na camA - anotace na camB
SELECT DISTINCT an1.dataset, an1.video, an1.camera, an2.camera, an1."offset", an1.track, an2.track, an1."object", an1.firsts[1], an2.firsts[1], an1.lasts[1], an2.lasts[1]
 FROM ONLY sunar.annotation_tracks an1 JOIN ONLY sunar.annotation_tracks an2 ON (an1.dataset=an2.dataset AND an1.video=an2.video AND an1.camera <> an2.camera)
 ORDER BY an1.dataset, an1.video, an1.camera, an2.camera;


-- takhle sezenu kombinace vsech kamer (1x)
SELECT DISTINCT an1.camera, an2.camera
FROM ONLY sunar.videos an1 JOIN ONLY sunar.videos an2 ON (an1.camera<an2.camera)
ORDER BY an1.camera, an2.camera

-- temp - skoro u konce - vyber pozitivnich vzorku na natrenovani multiple camera object 
SELECT an1.dataset, an1.video, an1.camera, an2.camera, an1."offset", an1."object", an1."time", st1.track, st2.track, 
       count(st1."time"), count(st2."time"), 
       avg(st1.position[0]), avg(st2.position[0]), avg(st1.position[1]), avg(st2.position[1]), 
       sum(sunar.overlaps(an1.position, an1.size, st1.position, st1.size)),
       sum(sunar.overlaps(an2.position, an2.size, st2.position, st2.size)) --, an1.firsts[1], an2.firsts[1], an1.lasts[1], an2.lasts[1]
 FROM ONLY sunar.annotations an1 JOIN ONLY sunar.annotations an2 ON (an1.dataset=an2.dataset AND an1.video=an2.video AND an1.camera<an2.camera AND an1."time"=an2."time"),
           sunar.processed st1, sunar.processed st2 -- joined @where
 WHERE an1.dataset=st1.dataset AND an1.video=st1.video AND an1.camera=st1.camera AND st1."object">0 AND st1."time">=an1."time"-5 AND st1."time"<=an1."time"+4 AND
       an2.dataset=st2.dataset AND an2.video=st2.video AND an2.camera=st2.camera AND st2."object">0 AND st2."time">=an2."time"-5 AND st2."time"<=an2."time"+4 AND
       st1."object"=st2."object" AND st1."time"=st2."time" AND
       
       an1.dataset=2 AND an1.video=2 -- for cameraA a B nezavisle na datasetu
 GROUP BY an1.dataset, an1.video, an1.camera, an2.camera, an1."offset", an1."object", an1."time", st1.track, st2.track
 HAVING count(st1."time")>4 AND count(st2."time")>4 AND 
        sum(sunar.overlaps(an1.position, an1.size, st1.position, st1.size)) > 0.5 AND
        sum(sunar.overlaps(an2.position, an2.size, st2.position, st2.size)) > 0.5
 ORDER BY an1.dataset, an1.video, an1.camera, an2.camera, an1."offset", an1."object", an1."time", st1.track, st2.track

-- jako negativni si vezmu tyhlety casy (zpracuju) a udelam jen ty, ktere se s anotaci nikdy nepotkaji (sum=0)