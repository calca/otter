import { getOrCreateClient } from '@google/stitch-sdk/dist/src/singleton.js';

async function main() {
  try {
    const client = getOrCreateClient();
    const tools = await client.listTools();
    console.log(JSON.stringify(tools, null, 2));
  } catch (e) {
    console.error(e);
  }
}

main();
