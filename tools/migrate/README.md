# Migration from Base44 → Supabase

Re-runnable (upsert by `legacy_id`). The device token is the user's own (`x-vc-token`); never commit it.

```bash
# people (via the old fcProxy)
curl -s -X POST "https://base44.app/api/apps/<beto-app>/functions/fcProxy" -H "x-vc-token: $VC_TOKEN" \
  -H "Content-Type: application/json" -d '{"action":"list","entity":"FaceContextPerson","limit":1000}' > people.json
python3 -c "import json;json.dump({'action':'import_people','people':json.load(open('people.json'))},open('imp.json','w'))"
curl -s -X POST https://vzqjwhlpwvqrosoiwiin.supabase.co/functions/v1/vc -H "x-vc-token: $VC_TOKEN" \
  -H "Content-Type: application/json" --data-binary @imp.json
```

Done 06/11/2026: 95 people (7 with an old face record), 3 things. The 20 encounters of 10/10 were Marcus's own test recordings; not imported yet (actions `import_encounters` is ready if wanted).
