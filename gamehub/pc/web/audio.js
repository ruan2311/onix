// Saída de som do jogo (AudioWorklet): uma fila de pedaços que o game.js manda a cada quadro.
// Conta quanto som ainda está na fila e avisa o game.js, que repassa ao núcleo para o ajuste
// dinâmico da taxa (o som estica ou encolhe até 0,5% para a fila ficar sempre no meio).
class OnixOut extends AudioWorkletProcessor {
  constructor() {
    super();
    this.chunks = [];
    this.pos = 0;
    this.queued = 0;
    this.tick = 0;
    this.port.onmessage = e => {
      if (e.data === 'clear') { this.chunks = []; this.pos = 0; this.queued = 0; return; }
      this.chunks.push(e.data);
      this.queued += e.data.length / 2;
      // Mais de 1/3 s na fila (a janela travou, por exemplo): joga fora o mais velho.
      while (this.queued > sampleRate / 3 && this.chunks.length > 1) this.queued -= this.chunks.shift().length / 2;
    };
  }
  process(_, outputs) {
    const out = outputs[0], l = out[0], r = out[1] || out[0];
    for (let i = 0; i < l.length; i++) {
      const c = this.chunks[0];
      if (!c) { l[i] = 0; r[i] = 0; continue; }   // faltou som: silêncio
      l[i] = c[this.pos];
      r[i] = c[this.pos + 1];
      this.pos += 2;
      if (this.pos >= c.length) { this.chunks.shift(); this.pos = 0; }
    }
    this.queued = Math.max(0, this.queued - l.length);
    if (++this.tick % 4 === 0) this.port.postMessage(this.queued);
    return true;
  }
}
registerProcessor('onix-out', OnixOut);
