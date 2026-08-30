export default {
  title: 'clono CSS統合検証',
  language: 'ja',
  entry: [
    {
      path: 'manuscript.md',
      theme: [
        {
          specifier: 'clono',
          import: 'styles/clono.css',
        },
        'user-theme.css',
      ],
    },
  ],
};
