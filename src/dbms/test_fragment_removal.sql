
-- OK:
DELETE FROM ONLY sunar.tracks WHERE (dataset, video, camera, track) IN (
     select dataset, video, camera, track
            -- max(position[0]) maxx, max(position[1]) maxy, min(position[0]) minx, min(position[1]) miny
      from ONLY sunar.states
      -- where dataset=2 and video=1 and camera=2
      group by dataset, video, camera, track
      having max(position[0]) - min(position[0]) < 20 and
             max(position[1]) - min(position[1]) < 15
);
