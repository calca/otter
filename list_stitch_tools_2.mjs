import { stitch } from '@google/stitch-sdk';

async function main() {
  try {
    const tools = await stitch.listTools();
    console.log(JSON.stringify(tools, null, 2));
  } catch (e) {
    console.error(e);
  }
}

main();
