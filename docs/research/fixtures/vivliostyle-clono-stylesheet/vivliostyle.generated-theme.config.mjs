export default {
  title: '生成先CSS統合検証',
  language: 'ja',
  entry: [
    {
      path: 'generated/manuscripts/manuscript.md',
      theme: [
        'generated/manuscripts/_clono/styles/clono.css',
        'user-theme.css',
      ],
    },
  ],
};
